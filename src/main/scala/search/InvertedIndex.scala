package search
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Try

/**
  * A thread-safe inverted index.  Read-only queries can be done fully in parallel.
  * Modifications are single-threaded, done by a background thread.
  *
  * @param exec a thread pool; it needs at least 2 threads
  * @tparam A the type of the sources being indexed (file, url, etc.)
  *
  * Modified this with a comment so I can re-submit program 5. Weird tag issue
  *
  */
class InvertedIndex[A](implicit exec: ExecutionContext) { index =>

  /**
    * The type of the internal table.  A reference to the internal table is returned by
    * modification methods.  Table are thread-safe (being immutable), but storing too many of
    * these tables could lead to memory leaks.
    */
  type Table = Map[String, Map[A, Locations]]

  /**
    * The queue of pending operations.
    * Methods `addRecords`, `addTokens`, `remove` and `freeze` add operations to this queue.
    */
  protected[this] val operations: SafeQueue[Operation] = SafeQueue.empty


  private var table: Table   = Map[String, Map[A, Locations]]()
  private var isFrozen       = false

  class MainThread extends Runnable {
    override def run(): Unit = {
      while(true) {
        val operation: Operation = operations.take()
        InvertedIndex.synchronized(operation.updateTable())

        if(operation.isInstanceOf[Freeze]) {
          return
        }
      }
    }
  }

  val mainThread = new MainThread()
  exec.execute(mainThread)


  /**
    * Operations used to modify the internal table.
    * This class implements 4 kinds of operations (adding records, adding tokens, removing a source
    * and freezing).  Classes that extend this class could create more.
    */
  protected trait Operation extends (Table => Table) {

    /**
      * Reads the current table, transforms it according to the `Table => Table` function, and
      * replaces it with the new table, which is also returned.
      */
    def updateTable(): Table = {
      val newTable: Table = apply(table)
      table = newTable
      table
    }
  }

  private class Freeze extends Operation {
    val promise: Promise[Table] = Promise[Table]

    override def apply(newTable: Table): Table = {
      this.promise.complete(Try(newTable))
      newTable
    }
  }

  /**
    * Freezes the index.
    * If the index is not yet frozen, this is done by adding a "freezing" operation at the back of
    * the operations queue. The method has no effect if the index is already frozen.
    *
    * @return a future that will contain the internal table as it is right after the operation is completed
    *
    */
  def freeze(): Future[Table] = {
    if(isFrozen.synchronized(isFrozen))
      Future(table)
    else {
      isFrozen = true
      val newOperation = new Freeze()
      operations.addLast(newOperation)
      newOperation.promise.future
    }
  }

  private class AddRecords(source: A, records: Records) extends Operation {
    val promise: Promise[Table] = Promise[Table]

    override def apply(oldTable: Table): Table = {
      val list = records.iterator.toList
      records.close()

      var newTable: Table = Map[String, Map[A, Locations]]()

      // newValue is updated new Map[file, Locations]
      for(tuple <- list) {

        // If the word already exists in the map
        if(oldTable.exists(_._1 == tuple._1)) {
          val newValue = oldTable(tuple._1) + (source -> tuple._2)
          newTable += (tuple._1 -> newValue)
        }

        // If the word does not already exist in the map
        else
          newTable += (tuple._1 -> Map(source -> tuple._2))
      }

      // If there are words in 'table' that aren't in 'newTable', add them to 'newTable'
      oldTable.foreach(tuple =>
        if(!newTable.exists(_._1 == tuple._1))
          newTable += (tuple._1 -> tuple._2)
      )

      this.promise.complete(Try(newTable))
      newTable
    }
  }

  /**
    * Adds records from a source to the index.
    * This is done by adding an operation to the back of the operations queue.
    * The records are read asynchronously, by a thread from the pool.
    *
    * @param source the source of the records
    * @return a future that will contain the internal table as it is right after the operation is completed
    *
    */
  def addRecords(source: A, records: Records): Future[Table] = {
    if(isFrozen.synchronized(isFrozen)) throw new IllegalStateException("addRecords(): Tried to call operation when frozen")

    else {
      val newOperation = new AddRecords(source, records)
      operations.addLast(newOperation)
      newOperation.promise.future
    }
  }

  private class Remover(source: A) extends Operation {
    val promise: Promise[Table] = Promise[Table]

    override def apply(oldTable: Table): Table = {
      var newTable: Table = oldTable

      for(tuple <- newTable) {

        val locations = tuple._2.get(source)

        // If there are any locations to remove
        if(locations.nonEmpty) {
          val newMap = tuple._2 - source

          // If we removed from a words associated map and there's still values left
          if(newMap.nonEmpty) {
            val newTuple = tuple._1 -> newMap
            newTable += newTuple
          }

          // If we removed and now the map is empty
          else {
            newTable -= tuple._1
          }
        }
      }


      this.promise.complete(Try(newTable))
      newTable
    }
  }

  /**
    * Removes a source.
    * This is done by adding an operation to the front of the operations queue.
    *
    * @return a future that will contain the internal table as it is right after the operation is completed
    *
    */
  def remove(source: A): Future[Table] = {
    if(isFrozen.synchronized(isFrozen)) throw new IllegalStateException("remove(): Tried to call operation when frozen")

    else {
      val newOperation = new Remover(source)
      operations.addFirst(newOperation)
      newOperation.promise.future
    }
  }

  /**
    * Adds tokens.
    * This is done by first computing records from the tokens (using [[InvertedIndex.getLocations]],
    * then inserting a record-adding operation into the back of the operations queue.
    * The computation of records is done asynchronously on one of the threads from the pool.
    * The records are read asynchronously, by a thread from the pool.
    *
    * @param rename an optional function to change the type of the source (e.g., from File to URL);
    *               if not specified, the implicit identity function will leave the source unchanged
    *
    * @tparam B the type of the source of the tokens
    *
    * @return a future that will contain the internal table as it is right after the operation is completed
    *
    */
  def addTokens[B](tokens: Tokens[B])(implicit rename: B => A): Future[Table] = {
    if(isFrozen.synchronized(isFrozen)) throw new IllegalStateException("addTokens(): Tried to call operation when frozen")

    else {
      Future {
        Future {
          val renamed = rename(tokens.source)
          val loc = InvertedIndex.getLocations(tokens)
          val records = Records.fromMap(loc)

          val newOperation = new AddRecords(renamed, records)
          operations.addLast(newOperation)
          Await.result(newOperation.promise.future, Duration.Inf)
        }
      }.flatten
    }
  }

  /**
    * Searches the index. Searches should be able to be done independent of 'table' lock.
    * For a given word, it returns a mapping from sources to locations.  If the word is not in the
    * index, the mapping is empty.
    */
  def lookup(str: String): Map[A, Locations] = this.synchronized {
    if(table.exists(_._1 == str))
      table(str)
    else
      Map[A, Locations]()
  }

  /** The number of words in the index. Searches should be able to be done independent of 'table' lock. **/
  def wordCount: Int = { this.synchronized(table.size) }

  /** The number of sources in the index. Searches should be able to be done independent of 'table' lock. **/
  def sourceCount: Int = {
    var listFiles: Set[A] = Set()

    // Get every key in the second map (keys are files here)
    this.synchronized {
      for (tuple <- table)
        tuple._2.foreach(map => listFiles += map._1)
    }
    listFiles.size
  }
}

/*** Companion object of the inverted index ***/
object InvertedIndex {

  /** Calculates the records (mapping from source to locations) of words in the tokens list **/
  def getLocations[B](tokens: Tokens[B]): Map[String, Locations] = {

    var firstMap = Map[String, List[Int]]()

    // wordList is a list of single words
    val wordList = tokens.iterator.toList
    tokens.close()

    // Go through each word, get the index of the word in the list, and add that index as a location to the map
    wordList.zipWithIndex.foreach {
      case (word, index) =>
        firstMap += (word -> (index + 1 :: (firstMap getOrElse(word, Nil)) ))
    }

    // Reverse the list of locations and return the new map
    val newMap: Map[String, Locations] = firstMap.map(tuple => tuple._1 -> Locations(tuple._2.reverse : _*))
    newMap
  }
}