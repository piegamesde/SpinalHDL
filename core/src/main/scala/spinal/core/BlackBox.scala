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


import spinal.core.internals._

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, LinkedHashSet}


/**
  * Create a generic for a BlackBox
  *
  * @example{{{
  *    class myMemory(sizeMem: Int) extends BlackBox{
  *        val generic = new Generic{
  *         val size = sizeMem
  *        }
  *        val io = new Bundle { ... }
  *    }
  * }}}
  */
class Generic {

}



/**
  * A blackbox allows the user to integrate an existing VHDL/Verilog component into the design by just specifying
  * the interfaces.
  *
  * @example{{{
  *    class Ram_1w_1r(wordWidth: Int, wordCount: Int) extends BlackBox {
  *        val generic = new Generic {
  *            val wordCount = Ram_1w_1r.this.wordCount
  *            val wordWidth = Ram_1w_1r.this.wordWidth
  *        }
  *        val io = new Bundle {
  *            val clk = in Bool
  *            val wr = new Bundle {
  *                val en   = in Bool
  *                val addr = in UInt (log2Up(wordCount) bit)
  *                val data = in Bits (wordWidth bit)
  *            }
  *            val rd = new Bundle {
  *                val en   = in Bool
  *                val addr = in UInt (log2Up(wordCount) bit)
  *                val data = out Bits (wordWidth bit)
  *            }
  *        }
  *        mapClockDomain(clock=io.clk)
  *   }
  * }}}
  */
abstract class BlackBox extends Component{
  val genericElements = ArrayBuffer[(String, Any)]()
  val librariesUsages = mutable.HashSet[String]();

  private var isBb = true
  def isBlackBox = isBb
  def setBlackBox() = isBb = true
  def clearBlackBox() = isBb = false

  def addGeneric(name : String, that : Any) : Unit = that match {
    case bt: BaseType => genericElements += Tuple2(name, bt.setName(name))
    case s: String    => genericElements += Tuple2(name, s)
    case i: Int       => genericElements += Tuple2(name, i)
    case i: BigInt if i <= Integer.MAX_VALUE && i >= Integer.MIN_VALUE => genericElements += Tuple2(name, i.toInt)
    case d: Double        => genericElements += Tuple2(name, d)
    case boolean: Boolean => genericElements += Tuple2(name, boolean)
    case t: TimeNumber    => genericElements += Tuple2(name, t)
  }

  def addGenerics(l : (String, Any)*) = l.foreach(e => addGeneric(e._1, e._2))

  val listRTLPath = new LinkedHashSet[String]()

  /** Add the path of the rtl file */
  def addRTLPath(path: String) = listRTLPath += path

  /** Return the generic of the blackbox */
  def getGeneric: Generic = {
    try {
      val m       = this.getClass.getMethod("generic")
      val generic = m.invoke(this).asInstanceOf[Generic]
      generic
    } catch {
      case _: Throwable => new Generic
    }
  }


  /**
    * Map clock domain signals (clock, reset, enable) to a clockDomain
    */
  def mapClockDomain(clockDomain: ClockDomain = ClockDomain.current, clock: Bool = null, reset: Bool = null, enable: Bool = null): Unit = {

    Component.push(parent)

    if (clockDomain.hasClockEnableSignal && enable == null) SpinalError(s"Clock domain has clock enable, but blackbox is not compatible $this")

    if (enable != null) {
      pulledDataCache += (clockDomain.clockEnable -> enable)
      enable := clockDomain.readClockEnableWire
    }

    if (reset != null) {
      if (!clockDomain.hasResetSignal) SpinalError(s"Clock domain has no reset, but blackbox need it $this")
      pulledDataCache += (clockDomain.reset -> reset)
      reset := clockDomain.readResetWire
    }

    pulledDataCache += (clockDomain.clock -> clock)
    clock := clockDomain.readClockWire

    Component.pop(parent)
  }


  /** Map clock domains signal to the current ClockDomain */
  def mapCurrentClockDomain(clock: Bool, reset: Bool = null, enable: Bool = null): Unit = {
    mapClockDomain(ClockDomain.current, clock, reset, enable)
  }

  override def isInBlackBoxTree: Boolean = isBlackBox || parent.isInBlackBoxTree

  /** Set the name of the blackbox */
  def setBlackBoxName(name: String): this.type = {
    this.definitionName = name
    this
  }

  /** Return true if the blackbox used the tag noNumericType */
  def isUsingNoNumericType = this.hasTag(noNumericType)

  /** Return true if the blackbox used std_ulogic */
  def isUsingULogic = this.hasTag(uLogic)

  /** Replace std_logic by std_ulogic */
  def replaceStdLogicByStdULogic() = this.addTag(uLogic)

  def isDefaultGenericValue = this.hasTag(addDefaultGenericValue)
}


/**
  * Create a blackBox with std_ulogic instead of std_logic
  */
abstract class BlackBoxULogic extends BlackBox {
  replaceStdLogicByStdULogic
}


/**
  * Create a Ulogic tag used by Blackbox in order to transform std_logic into std_ulogic
  */
object uLogic extends SpinalTag {
  override def moveToSyncNode = false
}


/**
  * Transform all unsigned/signed into std_logic_vector
  */
object noNumericType extends SpinalTag {
  override def moveToSyncNode = false
}


/**
  * In VHDL add the generic value in the definition of the blackbox
  */
object addDefaultGenericValue extends SpinalTag{
  override def moveToSyncNode = false
}