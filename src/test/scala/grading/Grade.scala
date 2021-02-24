package grading

import edu.unh.cs.mc.grading.GraderApp

object Grade extends GraderApp(
  30 -> new SampleSuite,
  10 -> new QueueSuite,
  30 -> new IndexBasicSuite,
  30 -> new IndexThreadSuite
)
