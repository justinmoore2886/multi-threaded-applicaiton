package grading

import java.util.concurrent.ThreadFactory

import edu.unh.cs.mc.utils.threads.{ TestThreadFactory, newUnlimitedThreadPool, withLocalContext }
import org.scalatest.FunSuite

import scala.concurrent.Future

class IndexBasicSuite extends FunSuite with GradingTests {

  import search.{ InvertedIndex, Locations, Records, Tokens }

  implicit val tf: ThreadFactory = TestThreadFactory

  val NoRecords = Records.fromMap(Map.empty)

  val source1 = "file1"
  val source2 = "file2"

  def tokens1 = Tokens(source1)("A", "B", "C", "A", "B", "D")

  def tokens2 = Tokens(source2)("A", "B", "C", "D", "E", "F")

  val map1 = Map(
    "A" -> Locations(1, 4),
    "B" -> Locations(2, 5),
    "C" -> Locations(3),
    "D" -> Locations(6)
  )

  val map2 = Map(
    "A" -> Locations(1),
    "B" -> Locations(2),
    "C" -> Locations(3),
    "D" -> Locations(4),
    "E" -> Locations(5),
    "F" -> Locations(6)
  )

  def records1 = Records.fromMap(map1)

  def records2 = Records.fromMap(map2)

  test("empty index") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      assert(index.wordCount === 0)
      assert(index.sourceCount === 0)
      assert(index.lookup("X").isEmpty)
      index.freeze().flatMap { table1 =>
        assert(table1.isEmpty)
        assert(index.wordCount === 0)
        assert(index.sourceCount === 0)
        assert(index.lookup("X").isEmpty)
        index.freeze().map { table2 =>
          assert(table2.isEmpty)
          assert(index.wordCount === 0)
          assert(index.sourceCount === 0)
          assert(index.lookup("X").isEmpty)
        }
      }
    }
  }

  test("double freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addRecords(source1, records1)
      val fr1 = index.freeze()
      val fr2 = index.freeze()
      for {
        table1 <- fr1
        table2 <- fr2
      } yield {
        assert(table1 === table2)
      }
    }
  }

  test("add 1 file (records)") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val added = index.addRecords(source1, records1)
      for (table <- added) yield {
        assert(index.wordCount === 4)
        assert(index.sourceCount === 1)
        assert(table === map1.mapValues(loc => Map(source1 -> loc)))
        assert(index.lookup("X").isEmpty)
        for ((word, loc) <- map1)
          assert(index.lookup(word) === Map(source1 -> loc))
        index.freeze()
      }
    }
  }

  test("add 1 file (tokens)") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val added = index.addTokens(tokens1)
      for (table <- added) yield {
        assert(index.wordCount === 4)
        assert(index.sourceCount === 1)
        assert(table.keySet === map1.keySet)
        assert(index.lookup("X").isEmpty)
        for ((word, loc) <- map1)
          assert(index.lookup(word) === Map(source1 -> loc))
        index.freeze()
      }
    }
  }

  test("add 1 file (records) and freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val added = index.addRecords(source1, records1)
      val frozen = index.freeze()
      for {
        table1 <- added
        table2 <- frozen
      } yield {
        assert(index.wordCount === 4)
        assert(index.sourceCount === 1)
        assert(table1.keySet === map1.keySet)
        assert(table2.keySet === map1.keySet)
        assert(index.lookup("X").isEmpty)
        for ((word, loc) <- map1)
          assert(index.lookup(word) === Map(source1 -> loc))
        assertThrows[IllegalStateException](index.addRecords("F", NoRecords))
        assertThrows[IllegalStateException](index.addTokens(tokens1))
        assertThrows[IllegalStateException](index.remove("F"))
      }
    }
  }

  test("add 1 file (tokens), then freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addTokens(tokens1).flatMap { table1 =>
        val frozen = index.freeze()
        for (table2 <- frozen) yield {
          assert(index.wordCount === 4)
          assert(index.sourceCount === 1)
          assert(table1.keySet === map1.keySet)
          assert(table2.keySet === map1.keySet)
          assert(index.lookup("X").isEmpty)
          for ((word, loc) <- map1)
            assert(index.lookup(word) === Map(source1 -> loc))
          assertThrows[IllegalStateException](index.addRecords("F", NoRecords))
          assertThrows[IllegalStateException](index.remove("F"))
          assertThrows[IllegalStateException](index.addTokens(tokens1))
        }
      }
    }
  }

  test("add 1 file (records), then remove it") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addRecords(source1, records1).flatMap { table1 =>
        val removed = index.remove(source1)
        for (table2 <- removed) yield {
          assert(index.wordCount === 0)
          assert(index.sourceCount === 0)
          assert(table1.keySet === map1.keySet)
          assert(table2.isEmpty)
          assert(index.lookup("X").isEmpty)
          assert(index.lookup("A").isEmpty)
          index.freeze()
        }
      }
    }
  }

  test("add 1 file (tokens), then remove it") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addTokens(tokens1).flatMap { table1 =>
        val removed = index.remove(source1)
        for (table2 <- removed) yield {
          assert(index.wordCount === 0)
          assert(index.sourceCount === 0)
          assert(table1.keySet === map1.keySet)
          assert(table2.isEmpty)
          assert(index.lookup("X").isEmpty)
          assert(index.lookup("A").isEmpty)
          index.freeze()
        }
      }
    }
  }

  test("add 1 file (records), then remove it and freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addRecords(source1, records1).flatMap { table1 =>
        val removed = index.remove(source1)
        val frozen = index.freeze()
        for {
          table2 <- removed
          table3 <- frozen
        } yield {
          assert(index.wordCount === 0)
          assert(index.sourceCount === 0)
          assert(table1.keySet === map1.keySet)
          assert(table2.isEmpty)
          assert(table3.isEmpty)
          assert(index.lookup("X").isEmpty)
          assert(index.lookup("A").isEmpty)
        }
      }
    }
  }

  test("add 1 file (tokens), then remove it and freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addTokens(tokens1).flatMap { table1 =>
        val removed = index.remove(source1)
        val frozen = index.freeze()
        for {
          table2 <- removed
          table3 <- frozen
        } yield {
          assert(index.wordCount === 0)
          assert(index.sourceCount === 0)
          assert(table1.keySet === map1.keySet)
          assert(table2.isEmpty)
          assert(table3.isEmpty)
          assert(index.lookup("X").isEmpty)
          assert(index.lookup("A").isEmpty)
        }
      }
    }
  }

  test("add 2 files (records) and freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addRecords(source1, records1)
      index.addRecords(source2, records2)
      index.freeze().map { table =>
        assert(index.wordCount === 6)
        assert(index.sourceCount === 2)
        assert(table.keySet === map1.keySet ++ map2.keySet)
        assert(index.lookup("A") === Map(source1 -> map1("A"), source2 -> map2("A")))
      }
    }
  }

  test("add 2 files (tokens), then freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      val add1 = index.addTokens(tokens1)
      val add2 = index.addTokens(tokens2)
      Future.sequence(List(add1, add2)).flatMap { _ =>
        index.freeze().map { table =>
          assert(index.wordCount === 6)
          assert(index.sourceCount === 2)
          assert(table.keySet === map1.keySet ++ map2.keySet)
          assert(index.lookup("A") === Map(source1 -> map1("A"), source2 -> map2("A")))
        }
      }
    }
  }

  test("add 1 file (records), then add another (records), remove first and freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addRecords(source2, records2).flatMap { _ =>
        index.addRecords(source1, records1)
        index.remove(source2)
        index.freeze().map { table =>
          assert(index.wordCount === 4)
          assert(index.sourceCount === 1)
          assert(table.keySet === map1.keySet)
          assert(index.lookup("A") === Map(source1 -> map1("A")))
        }
      }
    }
  }

  test("add 1 file (tokens), then add another (records), remove first and freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addTokens(tokens2).flatMap { _ =>
        index.addRecords(source1, records1)
        index.remove(source2)
        index.freeze().map { table =>
          assert(index.wordCount === 4)
          assert(index.sourceCount === 1)
          assert(table.keySet === map1.keySet)
          assert(index.lookup("A") === Map(source1 -> map1("A")))
        }
      }
    }
  }

  test("add 1 file (records), then another (tokens), then remove both and freeze") {
    withLocalContext(newUnlimitedThreadPool) { implicit exec =>
      val index = new InvertedIndex[String]
      index.addRecords(source1, records1)
      index.addTokens(tokens2).flatMap { table1 =>
        assert(index.wordCount === 6)
        assert(index.sourceCount === 2)
        assert(table1.keySet === map1.keySet ++ map2.keySet)
        assert(index.lookup("A") === Map(source1 -> map1("A"), source2 -> map2("A")))
        index.remove(source1)
        index.remove(source2)
        index.freeze().map { table2 =>
          assert(index.wordCount === 0)
          assert(index.sourceCount === 0)
          assert(table2.isEmpty)
          assert(index.lookup("A").isEmpty)
        }
      }
    }
  }

  for (n <- List(10, 100, 1000)) {
    test(s"add $n files (records) and freeze") {
      withLocalContext(newUnlimitedThreadPool) { implicit exec =>
        val files = List.tabulate(n)(i => s"file$i").toSet
        val locs = Map("A" -> Locations(1, 3), "B" -> Locations(2))
        val index = new InvertedIndex[String]
        for (file <- files)
          index.addRecords(file, Records.fromMap(locs))
        index.freeze().map { table =>
          assert(index.wordCount === 2)
          assert(index.sourceCount === n)
          assert(index.lookup("A").keySet === files)
          assert(index.lookup("A").values.map(_.values).toSet === Set(List(1, 3)))
          assert(index.lookup("B").values.map(_.values).toSet === Set(List(2)))
          assert(table.keySet === Set("A", "B"))
        }
      }
    }

    test(s"add $n files (tokens) in parallel, then freeze") {
      withLocalContext(newUnlimitedThreadPool) { implicit exec =>
        val files = List.tabulate(n)(i => s"file$i").toSet
        val index = new InvertedIndex[String]
        Future.traverse(files)(file => index.addTokens(Tokens(file)("A", "B", "A"))).flatMap { _ =>
          index.freeze().map { table =>
            assert(index.wordCount === 2)
            assert(index.sourceCount === n)
            assert(index.lookup("A").keySet === files)
            assert(index.lookup("A").values.map(_.values).toSet === Set(List(1, 3)))
            assert(index.lookup("B").values.map(_.values).toSet === Set(List(2)))
            assert(table.keySet === Set("A", "B"))
          }
        }
      }
    }

    test(s"add $n files (tokens) in parallel, then add and remove $n files (records) and freeze") {
      withLocalContext(newUnlimitedThreadPool) { implicit exec =>
        val files1 = List.tabulate(n)(i => s"someFile$i").toSet
        val files2 = List.tabulate(n)(i => s"otherFile$i").toSet
        val locs = Map("A" -> Locations(1, 3), "B" -> Locations(2))
        val index = new InvertedIndex[String]
        Future.traverse(files1)(file => index.addTokens(Tokens(file)("X", "Y", "Z"))).flatMap { _ =>
          for (file <- files2)
            index.addRecords(file, Records.fromMap(locs))
          for (file <- files1)
            index.remove(file)
          index.freeze().map { table =>
            assert(index.wordCount === 2)
            assert(index.sourceCount === n)
            assert(index.lookup("A").keySet === files2)
            assert(index.lookup("A").values.map(_.values).toSet === Set(List(1, 3)))
            assert(index.lookup("B").values.map(_.values).toSet === Set(List(2)))
            assert(table.keySet === Set("A", "B"))
          }
        }
      }
    }
  }
}
