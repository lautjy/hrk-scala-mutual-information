import scala.concurrent.Future

import org.scalatest._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play._
// import org.scalatest.PrivateMethodTester

// import org.mockito.Mockito._

import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._

import controllers.DataHandler._


class DataHandlerSpec extends PlaySpec with Results {

  "calcMI" should {
    "be symmetric" in {
      val l1 = Array(0, -1, 1, 1, -1, 0, 0, 1).map(_.toDouble)
      val l2 = Array(-1, 1, 0, 0, -1, 0, 1, -1).map(_.toDouble)
      val res1 = calcMI(l1, l2)
      val res2 = calcMI(l2, l1)
      res1 mustBe res2
    }
  }

  // Interesting - testing Private methods
  /* TODO: make this work. For now there is something stopping the imports
  from working properly
  "prepForJavaMI" should {
    "remove SKIP_FLAGs" in {
      val l1 = Array(0, -1, 1, 1, SKIP_FLAG, 0, 0, 1).map(_.toDouble)
      val l2 = Array(-1, 1, SKIP_FLAG, 0, -1, 0, 1, -1).map(_.toDouble)
      val prepForJavaMI = PrivateMethod[String]('prepForJavaMI)
      val (c1, c2) = DataHandler invokePrivate prepForJavaMI(l1, l2)

      c1 should not contain SKIP_FLAG
    }
  }
   */
}
