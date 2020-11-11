/* Author: Agastya Narumanchi
*
* This class is the fingertable implementation
* */
package chordAlgo

//import com.ashessin.cs441.project.workers.{Work, Worker}
import Actors.Work
import Actors.worker
import akka.actor.ActorRef


import scala.collection.mutable

object find_nextNode {
  def main(n: String, id: String, nextNode: String, refActor: ActorRef): Unit = {
    val finger = refActor.asInstanceOf[worker].finger
    if ((n.toInt until nextNode.toInt) contains id.toString)
      return nextNode
    else{
      val n_0 = nearest_previous_node.main(n, id, refActor)
      find_nextNode.main(n_0, id, nextNode, refActor);
    }
  }
}

object nearest_previous_node {
  def main(n: String, id: String, refActor: ActorRef): String = {
    val finger = refActor.asInstanceOf[worker].finger
    (1 to finger.size).foreach(i => {
      val starter = i.toString
      if (Range(n.toInt, n.toInt) contains finger(starter)._2.toInt)
        return finger(starter)._2
    })
    n
  }
}

/**
 *   n is the node
 *   p is the entry position in the finger table or key
 *   v is the finger table corresponding entries
 */
class Finger(n: Int, p: Int, v: Int) {

  val (pointer, value) = create

  private def create: (String, String) = {
    require(p <= v && 1 <= p , "K must satisfy 1<=p<=v criteria")
    v.toString -> ((n + Math.pow(2, p - 1)) % Math.pow(2, v)).toInt.toString
  }
}

/**
 *
 *  n is the node
 * totaln total number of nodes or positions
 */
class FTable(n: Int, totaln: Int) {

  val finger: mutable.TreeMap[String, (Range, String)] = create

  private def log2(x: Int): Int = (scala.math.log(x) / scala.math.log(2)).toInt

  private def create: mutable.TreeMap[String, (Range, String)] = {
    require(0 <= n, "Negetive position for node is invalid")
    require(1 <= totaln, "Minimum number of positions is 1")

    val fTable = mutable.TreeMap[String, (Range, String)]()
    (1 to log2(totaln)).foreach(k => {
      val finger = new Finger(n, k, log2(totaln))
      fTable.put(finger.pointer, (Range(0, 1), finger.value))
    })

    val a, b = Iterator.continually(fTable).flatten;
    b.next()
    (1 to log2(totaln)).foreach(i => {
      val starter = i.toString
      val begin = a.next()._2._2.toInt
      val end = b.next()._2._2.toInt
      if (starter == fTable.last._1)
      // if its the last pointer, reduce closing successor range by 1
        if (begin > end - 1)
          fTable(starter) = (Range(Math.abs(end - 1), begin), fTable(starter)._2)
        else
          fTable(starter) = (Range(begin, Math.abs(end - 1)), fTable(starter)._2)
      else if (begin > end)
        fTable(starter) = (Range(end, begin), fTable(starter)._2)
      else
        fTable(starter) = (Range(begin, end), fTable(starter)._2)
    })
    fTable
  }
}