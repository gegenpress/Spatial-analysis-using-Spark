package cse512

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.functions._

object HotcellAnalysis {
  Logger.getLogger("org.spark_project").setLevel(Level.WARN)
  Logger.getLogger("org.apache").setLevel(Level.WARN)
  Logger.getLogger("akka").setLevel(Level.WARN)
  Logger.getLogger("com").setLevel(Level.WARN)

def runHotcellAnalysis(spark: SparkSession, pointPath: String): DataFrame =
{
  // Load the original data from a data source
  var pickupInfo = spark.read.format("com.databricks.spark.csv").option("delimiter",";").option("header","false").load(pointPath);
  pickupInfo.createOrReplaceTempView("nyctaxitrips")
  pickupInfo.show()

  // Assign cell coordinates based on pickup points
  spark.udf.register("CalculateX",(pickupPoint: String)=>((
    HotcellUtils.CalculateCoordinate(pickupPoint, 0)
    )))
  spark.udf.register("CalculateY",(pickupPoint: String)=>((
    HotcellUtils.CalculateCoordinate(pickupPoint, 1)
    )))
  spark.udf.register("CalculateZ",(pickupTime: String)=>((
    HotcellUtils.CalculateCoordinate(pickupTime, 2)
    )))
  pickupInfo = spark.sql("select CalculateX(nyctaxitrips._c5),CalculateY(nyctaxitrips._c5), CalculateZ(nyctaxitrips._c1) from nyctaxitrips")
  var newCoordinateName = Seq("x", "y", "z")
  pickupInfo = pickupInfo.toDF(newCoordinateName:_*)
  pickupInfo.show()

  // Define the min and max of x, y, z
  val minX = -74.50/HotcellUtils.coordinateStep
  val maxX = -73.70/HotcellUtils.coordinateStep
  val minY = 40.50/HotcellUtils.coordinateStep
  val maxY = 40.90/HotcellUtils.coordinateStep
  val minZ = 1
  val maxZ = 31
  val numCells = (maxX - minX + 1)*(maxY - minY + 1)*(maxZ - minZ + 1)

  val cellValues = spark.sql("select x,y,z from pickupInfoView where x>= " + minX + " and x<= " + maxX + " and y>= " + minY + " and y<= " + maxY + " and z>= " + minZ + " and z<= " + maxZ + " order by z,y,x")
  cellValues.createOrReplaceTempView("cellValues")

  val hotCellValues = spark.sql("select x,y,z,count(*) as hotCellValues from cellValues group by z,y,x order by z,y,x").persist();
  hotCellValues.createOrReplaceTempView("hotCellValues")

  val sumOfPoints = spark.sql("select count(*) as countval, sum(hotCellValues) as sumval,sum(squared(hotCellValues)) as squaredsum from hotCellValues");
  sumOfPoints.createOrReplaceTempView("sumOfPoints")

  val mean = (sumOfPoints.first().getLong(0).toDouble / numCells.toDouble).toDouble
  
  spark.udf.register("squared", (inputX: Int) => ((HotcellUtils.squared(inputX))))
  
  val sumOfSquares = spark.sql("select sum(squared(hotCells)) as sumOfSquares from selectedCellHotness")
  sumOfSquares.createOrReplaceTempView("sumOfSquares")

  val std = math.sqrt(((sumOfPoints.first().getDouble(2) / numCells.toDouble) - (mean.toDouble * mean.toDouble))).toDouble

  spark.udf.register("neighbourCells", (minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int, inputX: Int, inputY: Int, inputZ: Int)
      => ((HotcellUtils.findNeighborCells(minX, minY, minZ, maxX, maxY, maxZ, inputX, inputY, inputZ))))
   val neighborCells = spark.sql("select neighborCells(n1.x, n1.y, n1.z, " + minX + "," + maxX + "," + minY + "," + maxY + "," + minZ + "," + maxZ + ") as neighborCellCount,"
      + "n1.x as x, n1.y as y, n1.z as z, "
      + "sum(n2.hotCellValues) as sumHotCells "
      + "from sumOfPoints as n1, sumOfPoints as n2 "
      + "where (n2.x = n1.x+1 or n2.x = n1.x or n2.x = n1.x-1) "
      + "and (n2.y = n1.y+1 or n2.y = n1.y or n2.y = n1.y-1) "
      + "and (n2.z = n1.z+1 or n2.z = n1.z or n2.z = n1.z-1) "
      + "group by n1.z, n1.y, n1.x "
      + "order by n1.z, n1.y, n1.x")
  neighborCells.createOrReplaceTempView("neighbors");

  spark.udf.register("GScore", (x: Int, y: Int, z: Int, mean:Double, std: Double, neighborCellCount: Int, sumHotCells: Int, numCells: Int) => ((
  HotcellUtils.findGScore(x, y, z, mean, std, neighborCellCount, sumHotCells, numCells))))

  val Neighbors = spark.sql("select GScore(x,y,z,"+mean+","+std+",neighborCellCount,sumHotCells,"+numCells+") as zScore,x, y, z from neighbors order by zScore desc");
  Neighbors.createOrReplaceTempView("NeighborsResult")
  
  val result = spark.sql("select x,y,z from NeighborsResult")
  result.createOrReplaceTempView("table")

  return result // YOU NEED TO CHANGE THIS PART
 }
}
