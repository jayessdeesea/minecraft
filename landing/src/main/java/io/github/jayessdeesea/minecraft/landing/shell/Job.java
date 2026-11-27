package io.github.jayessdeesea.minecraft.landing.shell;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The Windows job object a {@code landing} process puts itself in before anything else: every
 * process it starts, and every process those start, is a member - the kernel's record, inherited at creation and not
 * left (no breakaway is allowed). A kill ends every member but this process, by the job's own list, which Surefire's
 * forks and an MSYS fork's lost parent cannot hide from. The job is made to kill its members when its last handle
 * closes, and the handle is not inherited, so this process's exit, however it comes, ends whatever it left running.
 */
public final class Job implements Children {

    private static final int JOB_OBJECT_BASIC_PROCESS_ID_LIST = 3;
    private static final int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;
    private static final int JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;
    /** JOBOBJECT_EXTENDED_LIMIT_INFORMATION on 64-bit Windows, and where its LimitFlags lie. */
    private static final int EXTENDED_LIMIT_SIZE = 144;
    private static final int LIMIT_FLAGS_OFFSET = 16;
    private static final int ERROR_MORE_DATA = 234;
    /** How long a kill keeps listing and ending before it says the tree is not proven gone. */
    private static final Duration PROVEN = Duration.ofSeconds(10);
    private static final long LOOK_MILLIS = 50;

    private final MemorySegment job;
    private final MethodHandle query;
    private final VarHandle lastError;
    private final StructLayout state;
    private final long self = ProcessHandle.current().pid();

    private Job(MemorySegment job, MethodHandle query, VarHandle lastError, StructLayout state) {
        this.job = job;
        this.query = query;
        this.lastError = lastError;
        this.state = state;
    }

    /** This process, put in a new job; off Windows, or refused by Windows, it throws - there is no other way to kill. */
    public static Job enter() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            throw new UnsupportedOperationException("a job object is Windows': landing runs on Windows only");
        }
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        StructLayout state = Linker.Option.captureStateLayout();
        VarHandle lastError = state.varHandle(groupElement("GetLastError"));
        Linker.Option capture = Linker.Option.captureCallState("GetLastError");
        MethodHandle create = linker.downcallHandle(kernel32.find("CreateJobObjectW").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS), capture);
        MethodHandle set = linker.downcallHandle(kernel32.find("SetInformationJobObject").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT), capture);
        MethodHandle current = linker.downcallHandle(kernel32.find("GetCurrentProcess").orElseThrow(),
                FunctionDescriptor.of(ADDRESS));
        MethodHandle assign = linker.downcallHandle(kernel32.find("AssignProcessToJobObject").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), capture);
        MethodHandle query = linker.downcallHandle(kernel32.find("QueryInformationJobObject").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS), capture);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment called = arena.allocate(state);
            // no security attributes: the handle is not inherited, or a child would hold the job open past this exit
            MemorySegment job = (MemorySegment) create.invokeExact(called, MemorySegment.NULL, MemorySegment.NULL);
            if (job.address() == 0) {
                throw new IllegalStateException("CreateJobObjectW failed: " + (int) lastError.get(called, 0L));
            }
            MemorySegment limits = arena.allocate(EXTENDED_LIMIT_SIZE, 8);
            limits.set(JAVA_INT, LIMIT_FLAGS_OFFSET, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE);
            if ((int) set.invokeExact(called, job, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, limits, EXTENDED_LIMIT_SIZE) == 0) {
                throw new IllegalStateException("SetInformationJobObject failed: " + (int) lastError.get(called, 0L));
            }
            MemorySegment me = (MemorySegment) current.invokeExact();
            if ((int) assign.invokeExact(called, job, me) == 0) {
                throw new IllegalStateException("AssignProcessToJobObject failed: " + (int) lastError.get(called, 0L)
                        + " - is this process in a job that refuses it?");
            }
            return new Job(job, query, lastError, state);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("the job object not entered", t);
        }
    }

    /** The pids in the job now, this process among them; the list's buffer grows until it holds them all. */
    public List<Long> members() {
        long ids = 64;
        while (true) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment called = arena.allocate(state);
                long size = 8 + 8 * ids;
                MemorySegment list = arena.allocate(size, 8);
                int ok = (int) query.invokeExact(called, job, JOB_OBJECT_BASIC_PROCESS_ID_LIST, list, (int) size,
                        MemorySegment.NULL);
                if (ok == 0) {
                    int error = (int) lastError.get(called, 0L);
                    if (error == ERROR_MORE_DATA) {
                        ids *= 2;
                        continue;
                    }
                    throw new IllegalStateException("QueryInformationJobObject failed: " + error);
                }
                int listed = list.get(JAVA_INT, 4);
                List<Long> pids = new ArrayList<>(listed);
                for (int i = 0; i < listed; i++) {
                    pids.add(list.get(JAVA_LONG, 8 + 8L * i));
                }
                return pids;
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException("the job's members not listed", t);
            }
        }
    }

    /**
     * Lists the job, ends every pid but this one, lists again - until the list is this process alone, or ten seconds.
     * One child runs at a time, so every member but this one is {@code root}'s tree; {@code root} only names it.
     */
    @Override
    public boolean kill(ProcessHandle root) {
        Instant until = Instant.now().plus(PROVEN);
        while (true) {
            List<Long> others = members().stream().filter(pid -> pid != self).toList();
            if (others.isEmpty()) {
                return true;
            }
            if (!Instant.now().isBefore(until)) {
                return false;
            }
            others.forEach(pid -> ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly));
            try {
                Thread.sleep(LOOK_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** NORMAL_PRIORITY_CLASS: what the task's template asks for (Priority 4), and what its children inherit. */
    public static final int NORMAL_PRIORITY_CLASS = 0x20;

    /**
     * This process's priority class, {@code GetPriorityClass(GetCurrentProcess())}: 0 if Windows refused it. The task's
     * XML is the one home of the priority; the job sets none.
     */
    public static int priorityClass() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        MethodHandle current = linker.downcallHandle(kernel32.find("GetCurrentProcess").orElseThrow(),
                FunctionDescriptor.of(ADDRESS));
        MethodHandle priority = linker.downcallHandle(kernel32.find("GetPriorityClass").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
        try {
            return (int) priority.invokeExact((MemorySegment) current.invokeExact());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("the priority class not read", t);
        }
    }

    /** What is left in the job but this process, to name in a fault. */
    public List<Long> others() {
        return members().stream().filter(pid -> pid != self).toList();
    }
}
