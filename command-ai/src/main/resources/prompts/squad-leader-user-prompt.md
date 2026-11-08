«if(time)»
<time>
«time»
</time>

«endif»
<objective>
Mission: «mission»
Location: «location»
Intent: «intent»
«if(gather)»
Gather: «gather» - your soldiers collect it around the objective on their own
«endif»
«if(patrolBase)»
Patrol base: your soldiers build a shelter at the objective on their own and wait in it
«endif»
</objective>

<current_order>
«if(order)»
«order»
Its spot is «orderDistance» blocks from the objective.
«else»
None yet.
«endif»
</current_order>

<squad name="«name»">
«soldiers»</squad>

<terrain>
«terrain»</terrain>

<objective_ground>
«objectiveGround»</objective_ground>

Decide the squad's order now.