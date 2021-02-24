package grading

import java.util.concurrent.{ CountDownLatch, ThreadFactory }

import edu.unh.cs.mc.Implicits.IsTestThread
import edu.unh.cs.mc.utils.threads.{ KeepThreads, TestThreadFactory, newUnlimitedThreadPool, withLocalContext }
import org.scalatest.FunSuite
import scala.concurrent.Future

class IndexThreadSuite extends FunSuite with GradingTests {

  import search.{ InvertedIndex, Locations, Records, Tokens }

  implicit val tf: ThreadFactory = TestThreadFactory

  def records1 = Records.fromMap(Map("X" -> Locations(1), "Y" -> Locations(2)))

  def tokens1(file: String) = Tokens(file)("X", "Y")

  test("delayed add (records)") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val slow = new LatchRecords(records1)
      val add = index.addRecords("F", slow)
      val freeze = index.freeze()
      assert(!add.isCompleted)
      assert(!freeze.isCompleted)
      assert(index.wordCount === 0)
      assert(index.sourceCount === 0)
      assert(index.lookup("X").isEmpty)
      slow.latch.countDown()
      freeze.map { _ =>
        assert(add.isCompleted)
        assert(slow.callers.size === 1)
        assert(slow.callers.head.isTestThread)
        assert(index.wordCount === 2)
        assert(index.sourceCount === 1)
        assert(index.lookup("X").keySet === Set("F"))
      }
    }
  }

  test("queued add") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val slow = new LatchRecords(records1)
      val add1 = index.addRecords("F", slow)
      val add2 = index.addRecords("G", records1)
      assert(!add1.isCompleted)
      assert(!add2.isCompleted)
      assert(index.wordCount === 0)
      assert(index.sourceCount === 0)
      assert(index.lookup("X").isEmpty)
      slow.latch.countDown()
      add2.flatMap { _ =>
        assert(add1.isCompleted)
        assert(slow.callers.size === 1)
        assert(slow.callers.head.isTestThread)
        assert(index.wordCount === 2)
        assert(index.sourceCount === 2)
        assert(index.lookup("X").keySet === Set("F", "G"))
        index.freeze()
      }
    }
  }

  test("remove before add") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val slow = new LatchRecords(records1)
      val add1 = index.addRecords("F", slow)
      val add2 = index.addRecords("G", records1)
      val rem = index.remove("G")
      assert(!add1.isCompleted)
      assert(!add2.isCompleted)
      slow.latch.countDown()
      add2.flatMap { _ =>
        assert(add1.isCompleted)
        assert(rem.isCompleted)
        assert(slow.callers.size === 1)
        assert(slow.callers.head.isTestThread)
        assert(index.wordCount === 2)
        assert(index.sourceCount === 2)
        assert(index.lookup("X").keySet === Set("F", "G"))
        index.freeze()
      }
    }
  }

  test("delayed add (tokens)") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val slow = new LatchTokens(tokens1("F"))
      val add = index.addTokens(slow)
      assert(!add.isCompleted)
      assert(index.wordCount === 0)
      assert(index.sourceCount === 0)
      assert(index.lookup("X").isEmpty)
      slow.latch.countDown()
      add.flatMap { _ =>
        assert(slow.callers.size === 1)
        assert(slow.callers.head.isTestThread)
        assert(index.wordCount === 2)
        assert(index.sourceCount === 1)
        assert(index.lookup("X").keySet === Set("F"))
        index.freeze()
      }
    }
  }

  test("all records processed by the same thread") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val openLatch = new CountDownLatch(0)
      val slowRecords1 = new LatchRecords(records1, openLatch)
      val slowRecords2 = new LatchRecords(records1, openLatch)
      val add1 = index.addRecords("F", slowRecords1)
      val add2 = index.addRecords("G", slowRecords2)
      index.freeze().map { _ =>
        assert(add1.isCompleted)
        assert(add2.isCompleted)
        assert(slowRecords1.callers.size === 1)
        assert(slowRecords2.callers.size === 1)
        assert(slowRecords1.callers === slowRecords2.callers)
      }
    }
  }

  test("tokens and records processed by different threads") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val openLatch = new CountDownLatch(0)
      val slowTokens = new LatchTokens(tokens1("G"), openLatch)
      val slowRecords = new LatchRecords(records1, openLatch)
      val add1 = index.addRecords("F", slowRecords)
      add1.flatMap { _ =>
        val add2 = index.addTokens(slowTokens)
        add2.flatMap { _ =>
          assert(index.wordCount === 2)
          assert(index.sourceCount === 2)
          assert(index.lookup("X").keySet === Set("F", "G"))
          assert(slowTokens.callers.size === 1)
          assert(slowTokens.callers.head.isTestThread)
          assert(slowRecords.callers.size === 1)
          assert(slowRecords.callers.head.isTestThread)
          assert(slowTokens.callers.head ne slowRecords.callers.head)
          index.freeze()
        }
      }
    }
  }

  test("records jumps in front of tokens") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val slow = new LatchTokens(tokens1("G"))
      val add1 = index.addTokens(slow)
      val add2 = index.addRecords("F", records1)
      add2.flatMap { _ =>
        assert(!add1.isCompleted)
        assert(index.wordCount === 2)
        assert(index.sourceCount === 1)
        assert(index.lookup("X").keySet === Set("F"))
        slow.latch.countDown()
        add1.flatMap { _ =>
          assert(index.wordCount === 2)
          assert(index.sourceCount === 2)
          assert(index.lookup("X").keySet === Set("F", "G"))
          assert(slow.callers.size === 1)
          assert(slow.callers.head.isTestThread)
          index.freeze()
        }
      }
    }
  }

  test("records and remove jump in front of tokens") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val slow = new LatchTokens(tokens1("G"))
      val add1 = index.addTokens(slow)
      val add2 = index.addRecords("F", records1)
      val rem = index.remove("G")
      Future.sequence(List(add2, rem)).flatMap { _ =>
        assert(!add1.isCompleted)
        assert(index.wordCount === 2)
        assert(index.sourceCount === 1)
        assert(index.lookup("X").keySet === Set("F"))
        slow.latch.countDown()
        add1.flatMap { _ =>
          assert(index.wordCount === 2)
          assert(index.sourceCount === 2)
          assert(index.lookup("X").keySet === Set("F", "G"))
          assert(slow.callers.size === 1)
          assert(slow.callers.head.isTestThread)
          index.freeze()
        }
      }
    }
  }

  test("parallel adds (tokens)") {
    val n = 32 // threads
    val start = new CountDownLatch(n)
    val end = new CountDownLatch(1)
    val allTokens = List.tabulate(n)(i => new LatchTokens(Tokens(i)("A", "B", "C"), end))
    val runnerFactory = new TestThreadFactory with KeepThreads

    withLocalContext(newUnlimitedThreadPool(runnerFactory)) { implicit exec =>
      val index = new InvertedIndex[Int]
      val futures = Future.traverse(allTokens) { tokens =>
        Future {
          start.countDown()
          start.await()
          index.addTokens(tokens)
        }
      }
      futures.flatMap { lf =>
        end.countDown()
        Future.sequence(lf).flatMap { l =>
          for (table <- l)
            assert(table.keySet === Set("A", "B", "C"))
          val sizes = l.map(_ ("A").keys.size).toSet
          assert(sizes === (1 to n).toSet)
          val allCallers = allTokens.map(_.callers)
          for (set <- allCallers)
            assert(set.size === 1)
          val callers = allCallers.flatten.toSet
          assert(callers.subsetOf(runnerFactory.allThreads.toSet))
          assert(callers.size === n)
          index.freeze()
        }
      }
    }
  }

  test("parallel adds (records)") {
    val n = 32 // threads
    val start = new CountDownLatch(n)
    val end = new CountDownLatch(1)
    val allRecords = List.fill(n)(new LatchRecords(Records(
      "A" -> Locations(1), "B" -> Locations(2), "C" -> Locations(3)
    ), end))
    val runnerFactory = new TestThreadFactory with KeepThreads

    withLocalContext(newUnlimitedThreadPool(runnerFactory)) { implicit exec =>
      val index = new InvertedIndex[Int]
      val futures = Future.traverse(allRecords.zipWithIndex) {
        case (rec, i) =>
          Future {
            start.countDown()
            start.await()
            index.addRecords(i, rec)
          }
      }
      futures.flatMap { lf =>
        end.countDown()
        Future.sequence(lf).flatMap { l =>
          for (table <- l)
            assert(table.keySet === Set("A", "B", "C"))
          val sizes = l.map(_ ("A").keys.size).toSet
          assert(sizes === (1 to n).toSet)
          val allCallers = allRecords.map(_.callers)
          val callers = allCallers.flatten.toSet
          assert(callers.size === 1)
          assert(runnerFactory.allThreads.toSet.contains(callers.head))
          index.freeze()
        }
      }
    }
  }

}
