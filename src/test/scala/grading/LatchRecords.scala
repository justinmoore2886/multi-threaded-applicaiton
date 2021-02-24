package grading

import java.util.concurrent.CountDownLatch

import search.Records

class LatchRecords(records: Records, val latch: CountDownLatch = new CountDownLatch(1))
  extends Records {

  def close(): Unit = records.close()
  val iterator = new LatchIterator(records.iterator, latch)
  def callers: Set[Thread] = iterator.callers
}
