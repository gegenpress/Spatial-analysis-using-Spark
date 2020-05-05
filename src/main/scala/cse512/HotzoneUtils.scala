package cse512

object HotzoneUtils {

  def ST_Contains(queryRectangle: String, pointString: String ): Boolean = {
    val point = pointString.split(",")
    val pointX = point(0).trim().toDouble
    val pointY = point(1).trim().toDouble

    val rectangle = queryRectangle.split(",")
    val rectX1 =  rectangle(0).trim().toDouble
    val rectY1 =  rectangle(1).trim().toDouble
    val rectX2 =  rectangle(2).trim().toDouble
    val rectY2 =  rectangle(3).trim().toDouble


    val minX =  if(rectX1 < rectX2) rectX1 else rectX2
    val minY =  if(rectY1 < rectY2) rectY1 else rectY2
    val maxX =  if(rectX1 >= rectX2) rectX1 else rectX2
    val maxY =  if(rectY1 >= rectY2) rectY1 else rectY2

    if(pointX >= minX && pointX <= maxX && pointY >= minY && pointY <= maxY)
      return true
    else
      return false
  }

  // YOU NEED TO CHANGE THIS PART

}
