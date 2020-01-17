/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   HDL Core                         **
**       \__ \/ /_/ // //  |/ / /| | / /    (c) Dolu, All rights reserved    **
**      ___/ / ____// // /|  / ___ |/ /___                                   **
**     /____/_/   /___/_/ |_/_/  |_/_____/                                   **
**                                                                           **
**      This library is free software; you can redistribute it and/or        **
**    modify it under the terms of the GNU Lesser General Public             **
**    License as published by the Free Software Foundation; either           **
**    version 3.0 of the License, or (at your option) any later version.     **
**                                                                           **
**      This library is distributed in the hope that it will be useful,      **
**    but WITHOUT ANY WARRANTY; without even the implied warranty of         **
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU      **
**    Lesser General Public License for more details.                        **
**                                                                           **
**      You should have received a copy of the GNU Lesser General Public     **
**    License along with this library.                                       **
\*                                                                           */
package spinal.core

import spinal.core.internals.Misc





/**
  * Sometime, creating a Component to define some logic is overkill.
  * For this kind of cases you can use Area to define a group of signals/logic.
  *
  * @example {{{
  *     val tickConter = new Area{
  *       val tick = Reg(UInt(8 bits) init(0)
  *       tick := tick + 1
  *     }
  * }}}
  *  @see  [[http://spinalhdl.github.io/SpinalDoc/spinal/core/area/ Area Documentation]]
  */
trait Area extends Nameable with ContextUser with OwnableRef with ScalaLocated {

  component.addPrePopTask(reflectNames)

  override def toString: String = component.getPath() + "/" + super.toString()
}




/**
  * Create an Area which can be assign to a data
  *
  * @example {{{
  *     class Counter extends ImplicitArea[UInt]{
  *        val cnt = Reg(UInt(8 bits)
  *        ...
  *        override def implicitValue: UInt = cnt
  *     }
  *     val myCounter = Counter()
  *     io.myUInt = myCounter
  * }}}
  */
abstract class ImplicitArea[T] extends Area {
  def implicitValue: T
}

object ImplicitArea{
  implicit def toImplicit[T](area: ImplicitArea[T]): T = area.implicitValue
}


/**
  * Clock domains could be applied to some area of the design and then all synchronous elements instantiated into
  * this area will then implicitly use this clock domain.
  *
  *  @see  [[http://spinalhdl.github.io/SpinalDoc/spinal/core/clock_domain/ ClockDomain Documentation]]
  */
class ClockingArea(val clockDomain: ClockDomain) extends Area with DelayedInit {

  clockDomain.push()

  override def delayedInit(body: => Unit) = {
    body

    val bodyFunc = (body _)
    val field = bodyFunc.getClass.getDeclaredFields().apply(0)
    val a = bodyFunc.getClass.getDeclaredMethods().apply(0).getDeclaringClass.getName
    val b = this.getClass.getName  + "$delayedInit$body"
    val hit = a == b
    if(hit){
      clockDomain.pop()
    }
  }
}


/**
  * Clock Area with a specila clock enable
  */
class ClockEnableArea(clockEnable: Bool) extends Area with DelayedInit {

  val newClockEnable : Bool = if (ClockDomain.current.config.clockEnableActiveLevel == HIGH)
    ClockDomain.current.readClockEnableWire & clockEnable
  else
    ClockDomain.current.readClockEnableWire | !clockEnable

  val clockDomain = ClockDomain.current.copy(clockEnable = newClockEnable)

  clockDomain.push()

  override def delayedInit(body: => Unit) = {
    body

    if ((body _).getClass.getDeclaringClass == this.getClass) {
      clockDomain.pop()
    }
  }
}


/**
  * Define a clock domain which is x time slower than the current clock
  */
class SlowArea(factor: BigInt) extends ClockingArea(ClockDomain.current.newClockDomainSlowedBy(factor)){
  def this(frequency: HertzNumber) {
    this((ClockDomain.current.frequency.getValue / frequency).toBigInt())

    val factor = ClockDomain.current.frequency.getValue / frequency
    require(factor.toBigInt() == factor)
  }
}


/**
  * ResetArea allow to reset an area with a special reset combining with the current reset (cumulative)
  */
class ResetArea(reset: Bool, cumulative: Boolean) extends Area with DelayedInit {

  val newReset: Bool = if (ClockDomain.current.config.resetActiveLevel == LOW) {
    if(cumulative) (ClockDomain.current.readResetWire & !reset) else !reset
  }else {
    if(cumulative) (ClockDomain.current.readResetWire | reset) else reset
  }

  val clockDomain = ClockDomain.current.copy(reset = newReset)
  clockDomain.push()

  override def delayedInit(body: => Unit) = {
    body
    if ((body _).getClass.getDeclaringClass == this.getClass) {
      clockDomain.pop()
    }
  }
}

