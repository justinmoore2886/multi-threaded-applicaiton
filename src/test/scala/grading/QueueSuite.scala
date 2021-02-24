package grading

import java.util.concurrent.{ CountDownLatch, ThreadFactory }

import edu.unh.cs.mc.utils.threads.{ TestThreadFactory, newThreadPool, withContext }
import org.scalactic.TimesOnInt._
import org.scalatest.FunSuite

import scala.concurrent.Future
import scala.util.Random

class QueueSuite extends FunSuite with GradingTests {

  import search.SafeQueue

  class Adder(id: Int, m: Int, q: SafeQueue[Int], first: Option[Boolean] = None) {
    private val rand = new Random(2019 + id)

    private def add(first: Boolean): Int = {
      val n = rand.nextInt()
      if (first) q.addFirst(n) else q.addLast(n)
      n
    }

    def run(): Int = {
      var sum = 0
      first match {
        case Some(f) => m times (sum ^= add(f))
        case None    => m times (sum ^= add(rand.nextBoolean()))
      }
      sum
    }
  }

  class Taker(m: Int, q: SafeQueue[Int]) {
    def run(): Int = {
      var sum = 0
      m times (sum ^= q.take())
      sum
    }
  }

  for (
    (ma, mt, a, t, r) <- List(
      (100, 100, 10, 10, 20), (1000, 1000, 10, 10, 20), (10000, 10000, 10, 10, 20),
      (10000, 10000, 20, 20, 24), (10000, 10000, 20, 20, 64),
      (10000, 40000, 40, 10, 24), (10000, 40000, 40, 10, 64),
      (40000, 10000, 10, 40, 24), (40000, 10000, 10, 40, 64)
    )
  ) test(s"$a adders, $t takers, ${a * ma} messages, $r threads") {
    require(a * ma == t * mt)
    implicit val tf: ThreadFactory = new TestThreadFactory
    withContext(newThreadPool(r)) { implicit exec =>
      val queue = SafeQueue.empty[Int]
      val start = new CountDownLatch(r min (a + t))
      val adders = Future.traverse(1 to a) { id =>
        Future {
          val adder = new Adder(id, ma, queue)
          start.countDown()
          start.await()
          adder.run()
        }
      }
      val takers = Future.traverse(1 to t) { _ =>
        Future {
          val taker = new Taker(mt, queue)
          start.countDown()
          start.await()
          taker.run()
        }
      }
      for {
        adds <- adders
        takes <- takers
      } yield {
        val added = adds.foldLeft(0)(_ ^ _)
        val taken = takes.foldLeft(0)(_ ^ _)
        assert(taken === added)
      }
    }
  }
}

