package sample.cluster.playground.recursion

import scalaz.Functor


sealed trait TreeF[A]


case class Node[A](name:String, children:List[A]) extends TreeF[A]

case class Leaf[A](name:String) extends TreeF[A]


object TreeF {

  implicit val treeFunctor: Functor[TreeF] = new Functor[TreeF] {

    override def map[A, B](fa: TreeF[A])(f: A => B): TreeF[B] = fa match {
      case Node(name, children) => Node(name, children.map(n => f(n)))
      case Leaf(name) => Leaf(name)
    }

  }

}