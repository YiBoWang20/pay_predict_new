package predict.common

import mam.Dic
import mam.Utils.{printDf, udfBreak}
import org.apache.avro.SchemaBuilder.array
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}
import org.apache.spark.sql.functions.{avg, col, collect_list, explode, lit, substring, udf}
import org.apache.spark.ml.feature.Word2Vec

import scala.collection.mutable
//import org.apache.spark.ml.feature.V

object VideoVectorGenerate {
  def main(args: Array[String]): Unit = {
    System.setProperty("hadoop.home.dir","c:\\winutils")
    Logger.getLogger("org").setLevel(Level.ERROR)
    val spark: SparkSession = new sql.SparkSession.Builder()
      .appName("PredictVideoVectorGenerate")
      //.master("local[6]")
      .getOrCreate()
    //读取play数据
    //val hdfsPath=""
    val hdfsPath="hdfs:///pay_predict/"
    val playsProcessedPath=hdfsPath+"data/predict/common/processed/plays"
    val now=args(0)+" "+args(1)
    val plays = getData(spark,playsProcessedPath)
    //构建视频列表
    val playsList=getPlayList(plays,now)
    //用户每月平均观看视频32个
//    def udfGetLength=udf(getLength _)
//    def getLength(list:mutable.WrappedArray[String]) ={
//      list.length
//    }
//    playsList=playsList.withColumn("list_length",udfGetLength(col("video_list")))
//    playsList.show()
//    playsList.select(avg(col("list_length"))).show()

    printDf("plays_list",playsList)
    var videoDict=getVector(playsList)


    //将嵌入向量分成多列，便于后续模型的输入
    val vectorDimension=64
    for(i <- 0 to vectorDimension-1)
      videoDict=videoDict.withColumn("v_"+i,udfBreak(col("vector"),lit(i))).withColumnRenamed("word",Dic.colVideoId)

    printDf("videoDict",videoDict)

    val videoVectorPath=hdfsPath+"data/predict/common/processed/videovector"+args(0)
    videoDict.write.mode(SaveMode.Overwrite).format("parquet").save(videoVectorPath)
    //wordDict

  }

  
  def getData(spark:SparkSession,playsProcessedPath:String)={
   /**
   *@author wj
   *@param [spark, playsProcessedPath]
   *@return org.apache.spark.sql.Dataset<org.apache.spark.sql.Row>
   *@description 读取play数据
   */
    spark.read.format("parquet").load(playsProcessedPath)
  }
  def getPlayList(plays:DataFrame,now:String) ={
    /**
    *@author wj
    *@param [plays, now]
    *@return org.apache.spark.sql.Dataset<org.apache.spark.sql.Row>
    *@description 按照用户和月份构建播放列表
    */
    var playsList=plays.withColumn("play_month",substring(col(Dic.colPlayEndTime),0,7))
    playsList=playsList
      .filter(col(Dic.colPlayEndTime).<(now))
      .groupBy(col(Dic.colUserId),col("play_month"))
      .agg(collect_list(col(Dic.colVideoId)).as("video_list"))
    playsList
  }
  def getVector(playsList:DataFrame) ={
    /**
    *@author wj
    *@param [playsList]
    *@return org.apache.spark.sql.Dataset<org.apache.spark.sql.Row>
    *@description  训练Word2vector模型，得到视频的嵌入向量
    */
    val vectorDimension=64
    val windowSize=10  //默认参数为5，这里尝试设置为10，在一定程度上，windowSize越大，训练越慢,但是向量表达更准确
    val w2vModel=new Word2Vec()
      .setInputCol("video_list")
      .setOutputCol("result")
      .setVectorSize(vectorDimension)
      .setWindowSize(windowSize)
      .setMinCount(5)
    val model=w2vModel.fit(playsList)

    //print("滑动窗口的大小："+w2vModel.getWindowSize)
    //val result=model.transform(playsList)
    model.getVectors
  }

}
