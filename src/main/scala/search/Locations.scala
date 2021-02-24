package search

object Locations {
  def fromValues(values: Iterable[Int]): Locations = new ArrayLocations(values.toArray)

  def apply(values: Int*): Locations = fromValues(values)
}

/** A list of word locations.  The list contains only positive values, is sorted and is non-empty.*/
trait Locations {
  def values: List[Int]
}

/** Array-based implementation, which should be more compact than lists. */
private class ArrayLocations(array: Array[Int]) extends Locations {

  def values: List[Int] = array.toList

  override def toString: String = array.mkString("[", ", ", "]")

  override def equals(obj: Any): Boolean = obj match {
    case that: Locations => this.values == that.values
    case _               => false
  }

  override def hashCode(): Int = values.##
}
