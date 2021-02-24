package grading

import org.scalatest.Suites

class AllTests extends Suites(
  new SampleSuite,
  new QueueSuite,
  new IndexBasicSuite,
  new IndexThreadSuite
)
