package grading

import java.util.concurrent.{ CountDownLatch, Executors }

import edu.unh.cs.mc.Implicits.IsTestThread
import edu.unh.cs.mc.utils.threads.TestThreadFactory
import org.scalatest.FunSuite
import search._
import org.scalatest.tagobjects.Slow

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }

class SampleTests extends FunSuite {

  val file1 = "how I want a drink alcoholic of course".split(' ')
  val file2 = "I want to know how to pass this course".split(' ')

  def tokens1 = Tokens("file1")(file1: _*)
  def tokens2 = Tokens("file2")(file2: _*)

  val map1 = Map(
    "how" -> Locations(1),
    "I" -> Locations(2),
    "want" -> Locations(3),
    "a" -> Locations(4),
    "drink" -> Locations(5),
    "alcoholic" -> Locations(6),
    "of" -> Locations(7),
    "course" -> Locations(8)
  )

  val map2 = Map(
    "I" -> Locations(1),
    "want" -> Locations(2),
    "to" -> Locations(3, 6),
    "know" -> Locations(4),
    "how" -> Locations(5),
    "pass" -> Locations(7),
    "this" -> Locations(8),
    "course" -> Locations(9)
  )

  def records1 = Records.fromMap(map1)
  def records2 = Records.fromMap(map2)

  test("SafeQueue 1") {
    val q = SafeQueue.empty[Int]
    q.addFirst(3)
    q.addLast(4)
    q.addFirst(2)
    q.addFirst(1)
    q.addLast(5)
    assert(List.fill(5)(q.take()) === (1 to 5))
  }

  test("SafeQueue 2") {
    import scala.concurrent.ExecutionContext.Implicits.global

    val q = SafeQueue.empty[String]
    q.addLast("B")
    q.addFirst("A")
    assert(q.take() === "A")
    assert(q.take() === "B")

    val p = Promise[Thread]
    val f = Future {
      p.success(Thread.currentThread)
      try q.take() catch {
        case _: InterruptedException => "Interrupted"
      }
    }
    val thread = Await.result(p.future, 1.second)
    assert(!f.isCompleted)
    thread.interrupt()
    assert(Await.result(f, 1.second) === "Interrupted")
  }

  test("getLocations") {
    assert(InvertedIndex.getLocations(tokens1) === map1)
    assert(InvertedIndex.getLocations(tokens2) === map2)
  }

  test("test 1") {
    val exec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)
    val index = new InvertedIndex[String]()(exec)

    val add1 = index.addRecords("file1", records1)
    val table1 = Await.result(add1, 1.second)
    assert(table1.keySet === file1.toSet)
    assert(table1("want") === Map("file1" -> Locations(3)))
    assert(index.lookup("want") === Map("file1" -> Locations(3)))
    assert(index.lookup("pass").isEmpty)
    assert(index.wordCount === 8)
    assert(index.sourceCount === 1)

    val add2 = index.addRecords("file2", records2)
    val table2 = Await.result(add2, 1.second)
    assert(table2.keySet === file1.toSet ++ file2.toSet)
    assert(table2("want") === Map("file1" -> Locations(3), "file2" -> Locations(2)))
    assert(index.lookup("want") === Map("file1" -> Locations(3), "file2" -> Locations(2)))
    assert(index.lookup("to") === Map("file2" -> Locations(3, 6)))
    assert(index.wordCount === 12)
    assert(index.sourceCount === 2)

    val rem1 = index.remove("file1")
    val table3 = Await.result(rem1, 1.second)
    assert(table3.keySet === file2.toSet)
    assert(table3("want") === Map("file2" -> Locations(2)))
    assert(index.lookup("want") === Map("file2" -> Locations(2)))
    assert(index.lookup("drink").isEmpty)
    assert(index.wordCount === 8)
    assert(index.sourceCount === 1)

    val rem2 = index.remove("file2")
    val table4 = Await.result(rem2, 1.second)
    assert(table4.isEmpty)
    assert(index.lookup("want").isEmpty)
    assert(index.wordCount === 0)
    assert(index.sourceCount === 0)

    index.freeze()
    assertThrows[IllegalStateException](index.addRecords("file1", records1))
    assertThrows[IllegalStateException](index.remove("file2"))
    assertThrows[IllegalStateException](index.addTokens(tokens1))
    assert(index.lookup("want").isEmpty)
    assert(index.wordCount === 0)
    assert(index.sourceCount === 0)

    exec.shutdown()
    assert(exec.awaitTermination(5, SECONDS))
  }

  test("test 2") {
    val exec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(TestThreadFactory))
    val index = new InvertedIndex[String]()(exec)

    val openLatch = new CountDownLatch(0)
    val rec1 = new LatchRecords(records1, openLatch)
    val rec2 = new LatchRecords(records2, openLatch)
    val add1 = index.addRecords("file1", rec1)
    val add2 = index.addRecords("file2", rec2)

    val table1 = Await.result(add1, 1.second)
    assert(table1.keySet === file1.toSet)
    assert(table1("want") === Map("file1" -> Locations(3)))

    val table2 = Await.result(add2, 1.second)
    assert(table2.keySet === file1.toSet ++ file2.toSet)
    assert(table2("want") === Map("file1" -> Locations(3), "file2" -> Locations(2)))

    val callers = rec1.callers
    assert(callers === rec2.callers)
    assert(callers.size === 1)
    assert(callers.head.isTestThread)

    assert(index.lookup("want") === Map("file1" -> Locations(3), "file2" -> Locations(2)))
    assert(index.lookup("to") === Map("file2" -> Locations(3, 6)))
    assert(index.wordCount === 12)
    assert(index.sourceCount === 2)

    index.freeze()
    exec.shutdown()
    assert(exec.awaitTermination(5, SECONDS))
  }

  test("test 3") {
    val exec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(TestThreadFactory))
    val index = new InvertedIndex[String]()(exec)

    val tok1 = new LatchTokens(tokens1)
    val rec2 = new LatchRecords(records2)
    val add1 = index.addTokens(tok1)
    val add2 = index.addRecords("file2", rec2)

    tok1.latch.countDown()
    assert(!add1.isCompleted)
    assert(!add2.isCompleted)

    rec2.latch.countDown()
    val table1 = Await.result(add2, 1.second)
    assert(table1.keySet === file2.toSet)
    assert(table1("want") === Map("file2" -> Locations(2)))

    val table2 = Await.result(add1, 1.second)
    assert(table2.keySet === file1.toSet ++ file2.toSet)
    assert(table2("want") === Map("file1" -> Locations(3), "file2" -> Locations(2)))

    val callers1 = tok1.callers
    val callers2 = rec2.callers
    assert(callers1.size === 1)
    assert(callers2.size === 1)
    assert(callers1.head.isTestThread)
    assert(callers2.head.isTestThread)
    assert(callers1.head ne callers2.head)

    index.freeze()
    exec.shutdown()
    assert(exec.awaitTermination(5, SECONDS))
  }

  test("test 4") {
    val exec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)
    val index = new InvertedIndex[String]()(exec)

    val rec1 = new LatchRecords(records1)
    val rec2 = new LatchRecords(records2, new CountDownLatch(0))
    val add1 = index.addRecords("file1", rec1)
    val add2 = index.addRecords("file2", rec2)
    index.remove("file2")
    assert(!add1.isCompleted)
    assert(!add2.isCompleted)

    rec1.latch.countDown()
    val table1 = Await.result(add1, 1.second)
    assert(table1.keySet === file1.toSet)
    assert(table1("want") === Map("file1" -> Locations(3)))

    val table2 = Await.result(add2, 1.second)
    assert(table2.keySet === file1.toSet ++ file2.toSet)
    assert(table2("want") === Map("file1" -> Locations(3), "file2" -> Locations(2)))

    index.freeze()
    exec.shutdown()
    assert(exec.awaitTermination(5, SECONDS))
  }

  test("test 5", Slow) {
    import scala.concurrent.ExecutionContext.Implicits.global
    val index = new InvertedIndex[String]

    val n = 10000
    val initFiles = List.tabulate(n)(i => s"init-$i")
    val tokens = List.tabulate(n)(i => Tokens(s"tokens-$i")(file2: _*))
    val records = List.tabulate(n)(i => s"records-$i")

    val init = Future.traverse(initFiles)(index.addRecords(_, records1))
    Await.ready(init, 10.seconds)
    assert(index.wordCount === 8)
    assert(index.sourceCount === n)

    val add1 = Future.traverse(tokens)(index.addTokens(_)(identity))
    Future.traverse(records)(index.addRecords(_, records2))
    Future.traverse(initFiles)(index.remove)
    Await.ready(add1, 10.seconds)
    val table = Await.result(index.freeze(), 10.seconds)

    assert(index.wordCount === 8)
    assert(index.sourceCount == 2 * n)
    assert(index.lookup("drink").isEmpty)
    assert(index.lookup("pass").nonEmpty)
    assert(table.keys === file2.toSet)
    assert(table("want").keys === records.toSet ++ tokens.map(_.source))
  }

  test("test 6") {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val strLen: String => Int = _.length

    val index = new InvertedIndex[Int]
    val add = index.addTokens(tokens1)
    val table = Await.result(add, 1.second)
    assert(table.keySet === file1.toSet)
    assert(table("want") === Map(5 -> Locations(3)))
    assert(index.wordCount === 8)
    assert(index.sourceCount === 1)
    Await.ready(index.freeze(), 5.seconds)
  }
}
