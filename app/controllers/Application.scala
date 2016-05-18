package controllers

import play.api._
import play.api.db._
import play.api.mvc._
import play.api.Logger
import play.api.libs.json._
import play.api.cache.Cache
import play.api.Play.current
import play.api.libs.functional.syntax._

import DataHandler._


object Application extends Controller {
  val log = Logger("main")
  lazy val varNames = DataHandler.getNames

  def index = Action {
    Ok(views.html.index(null))
  }

  /** Send available variable names as JSON list */
  def listAvailableNames = Action {
    val jsonArrayOfStrings = Json.toJson(varNames)
    Ok(jsonArrayOfStrings)
  }

  /** Get JSON data for a variable of its MI and d
    *
    * TODO: rewrite
    * Mutual information
    *  provides a robust measure of dependency between random variables
    *  (constrast with correletion).
    *
    * Responds 404 with JSON explanation if inName not found in data,
    *  and JSON list of Mutual Informations for every other variable.
    *
    * @param inName: name of variable that was in data
    * */
  def mutual(inName: String) = Action {
    log.info(s"MI requested for $inName")

    // Darn this horror. I spent over 2h messing around trying to
    // unroll results into JSON from all kinds of things. In the end
    // had to resort to this silly Map(mi -> (d, varname)).
    val res = DataHandler.getResults(inName)
    val json = Json.toJson(res.map {
      case (k,v) => (k,v)
    }.toList.sortBy{- _._1}.map(
      k => {
        Json.obj(
          "mi" -> k._1,
          "d" -> k._2._1,
          "variable_name" -> k._2._2
        )
      }
    ))

    lazy val errorMessageFor404 = Json.obj(
      "msg" -> s"NotFound: variable '$inName' not available.",
      "available_names" -> Json.toJson(varNames)
    )
    DataHandler.getNames.find(x=>x == inName) match {
      case Some(_) => Ok(json)
      case None => NotFound(errorMessageFor404)
    }
  }

  /** Leaving this here for possible DB work
   *
   * This is code from the original example.
   * */
  def db = Action {
    var out = ""
    val conn = DB.getConnection()
    try {
      val stmt = conn.createStatement

      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)")
      stmt.executeUpdate("INSERT INTO ticks VALUES (now())")

      val rs = stmt.executeQuery("SELECT tick FROM ticks")
      while (rs.next) {
        out += "Read from DB: " + rs.getTimestamp("tick") + "\n"
      }
    } finally {
      conn.close()
    }
    Ok(out)
  }
}
