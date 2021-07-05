package io.smc.exporter

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util

import io.circe.Decoder
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.{Collector, GaugeMetricFamily}

import scala.jdk.CollectionConverters._
import scala.sys.process.Process
import scala.util.Try

object SmcExporter {

  private val Port           = sys.env.getOrElse("PORT", "7777").toInt
  private val SmcCommandPath = sys.env.get("SMC_COMMAND_PATH")

  def main(args: Array[String]): Unit = {
    val smcCommand = new SmcCommand(commandPath)
    new FanSpeedSensors(smcCommand).register()
    new TemperatureSensors(smcCommand).register()
    new HTTPServer(Port)
  }

  private def commandPath: String = SmcCommandPath match {
    case Some(path) => path
    case None => {
      val file = File.createTempFile("smc", null)
      file.delete()
      Files.copy(getClass.getResourceAsStream("/smc"), file.toPath)

      println(s"Created smc file: $file")

      val perms = new util.HashSet[PosixFilePermission]
      perms.add(PosixFilePermission.OWNER_READ)
      perms.add(PosixFilePermission.OWNER_WRITE)
      perms.add(PosixFilePermission.OWNER_EXECUTE)
      Files.setPosixFilePermissions(file.toPath, perms)
      file.getAbsolutePath
    }
  }
}

case class FanInfo(
    fanId: String,
    currentSpeed: Double,
    minSpeed: Double,
    maxSpeed: Double,
    safeSpeed: Double,
    targetSpeed: Double,
    mode: String
)

object FanInfo {
  implicit val decoder: Decoder[FanInfo] = io.circe.generic.semiauto.deriveDecoder
}

class SmcCommand(smc: String) {
  def readTemperatures(): Map[String, Double] = {
    val resultE = for {
      stdout <- Try(Process(List(smc, "-t", "-o", "json")).!!).toEither
      json   <- io.circe.parser.parse(stdout)
      map    <- json.as[Map[String, Double]]
    } yield map

    resultE.right.get
  }

  def readFanSpeeds(): List[FanInfo] = {
    val resultE = for {
      stdout <- Try(Process(List(smc, "-f", "-o", "json")).!!).toEither
      json   <- io.circe.parser.parse(stdout)
      fans   <- json.as[List[FanInfo]]
    } yield fans

    resultE.right.get
  }
}

class FanSpeedSensors(smcCommand: SmcCommand) extends Collector {
  override def collect(): util.List[Collector.MetricFamilySamples] = {
    val samples = new util.ArrayList[Collector.MetricFamilySamples]()
    smcCommand.readFanSpeeds().zipWithIndex.foreach {
      case (info, idx) =>
        val current = new GaugeMetricFamily(
          "osx_smc_fan_current_rpm",
          "Fan speed reading",
          List("index", "name", "mode").asJava
        )
        current.addMetric(
          List(idx.toString, info.fanId.trim, info.mode).asJava,
          info.currentSpeed
        )

        val min = new GaugeMetricFamily(
          "osx_smc_fan_min_rpm",
          "Fan speed reading",
          List("index", "name", "mode").asJava
        )
        min.addMetric(
          List(idx.toString, info.fanId.trim, info.mode).asJava,
          info.minSpeed
        )

        val max = new GaugeMetricFamily(
          "osx_smc_fan_max_rpm",
          "Fan speed reading",
          List("index", "name", "mode").asJava
        )
        max.addMetric(
          List(idx.toString, info.fanId.trim, info.mode).asJava,
          info.maxSpeed
        )

        val target = new GaugeMetricFamily(
          "osx_smc_fan_target_rpm",
          "Fan speed reading",
          List("index", "name", "mode").asJava
        )
        target.addMetric(
          List(idx.toString, info.fanId.trim, info.mode).asJava,
          info.targetSpeed
        )

        samples.add(current)
        samples.add(min)
        samples.add(max)
        samples.add(target)
    }

    samples
  }
}

class TemperatureSensors(smcCommand: SmcCommand) extends Collector {
  val SensorNames = Map(
    "TA0P" -> "AMBIENT_AIR_0",
    "TA1P" -> "AMBIENT_AIR_1",
    "TC0F" -> "CPU_0_DIE",
    "TC0D" -> "CPU_0_DIODE",
    "TC0H" -> "CPU_0_HEATSINK",
    "TC0P" -> "CPU_0_PROXIMITY",
    "TB0T" -> "ENCLOSURE_BASE_0",
    "TB1T" -> "ENCLOSURE_BASE_1",
    "TB2T" -> "ENCLOSURE_BASE_2",
    "TB3T" -> "ENCLOSURE_BASE_3",
    "TG0D" -> "GPU_0_DIODE",
    "TG0H" -> "GPU_0_HEATSINK",
    "TG0P" -> "GPU_0_PROXIMITY",
    "TH0P" -> "HDD_PROXIMITY",
    "Th0H" -> "HEATSINK_0",
    "Th1H" -> "HEATSINK_1",
    "Th2H" -> "HEATSINK_2",
    "TL0P" -> "LCD_PROXIMITY",
    "TM0S" -> "MEM_SLOT_0",
    "TM0P" -> "MEM_SLOTS_PROXIMITY",
    "Tm0P" -> "MISC_PROXIMITY",
    "TN0H" -> "NORTHBRIDGE",
    "TN0D" -> "NORTHBRIDGE_DIODE",
    "TN0P" -> "NORTHBRIDGE_PROXIMITY",
    "TO0P" -> "ODD_PROXIMITY",
    "Ts0P" -> "PALM_REST",
    "Tp0P" -> "PWR_SUPPLY_PROXIMITY",
    "TI0P" -> "THUNDERBOLT_0",
    "TI1P" -> "THUNDERBOLT_1"
  )

  override def collect(): util.List[Collector.MetricFamilySamples] = {
    val metric = new GaugeMetricFamily(
      s"osx_smc_temperature_sensor",
      "Temperature sensor reading",
      util.Arrays.asList("sensor_code", "sensor_name")
    )
    smcCommand.readTemperatures().foreach {
      case (code, value) =>
        metric.addMetric(
          util.Arrays.asList(code, SensorNames.getOrElse(code, code)),
          value
        )
    }

    List(metric: Collector.MetricFamilySamples).asJava
  }
}
