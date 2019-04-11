package com.odenzo.ripple.apps

import com.odenzo.ripple.models.support.RippleRq
//
//
//object tc {
//object MyTypeClass {
//
//   def apply[T: MyTypeClass]: MyTypeClass[T] = implicitly[MyTypeClass[T]]
//
//   // def append[A: MyTypeClass]: (a1 : A, a2: A): A = apply[A].append(a1, a2)
//
//   // def concat[A: MyTypeClass](list: List[A]): A = apply[A].concat(list)
//}
//
//
//// The type class
////
//// The type class itself is a trait with a single type parameter.
//
//  trait MyTypeClass[A] {
//
//    def myKeyFunction[A](value: A): String
//    // Multiple functions of coursew
//  }
//
//// Type class interface
////
//// The interface consists of one or more methods
//// that accept type class instances as an `implicit` parameter list.
//
//def toMyKeyFunction[A] (value: A) (implicit tripeclass: MyTypeClass[A] ): String = {
//    tripeclass.myKeyFunction(value)
//}
//
//// Here we define a convenience constructor for type class instances.
////
//// This isn't a core part of the type class pattern,
//// but it helps keep the instance definitions short.
//
//object MyTypeClass {
//  def apply[A](func: A => String) = new MyTypeClass[A] {
//    def myKeyFunction(value: A): String = func(value)
//  }
//}
//
//  implicit val   rqTypeClass:MyTypeClass[RippleRq] = {
//    MyTypeClass( (rq:RippleRq) ⇒ rq.id.toString )
//  }
//}
