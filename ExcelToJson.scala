package scala

import java.nio.file.Paths
import org.apache.poi.ss.usermodel.{WorkbookFactory, DataFormatter}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import scala.collection.mutable

object ExcelToJsonConverter extends App {
  // Вывод текущей рабочей директории
  println(s"Текущая рабочая директория: ${Paths.get("").toAbsolutePath}")

  // Загрузка Excel-файла
  val fileSource = "C:\\li4kmk\\cool\\table_map_v1.7.xlsx"
  val workbook = WorkbookFactory.create(new java.io.File(fileSource))
  val sheetТарелки = workbook.getSheet("тарелки")
  val sheetШтрудели = workbook.getSheet("штрудели")

  val dataFormatter = new DataFormatter()

  // Функция для получения значения ячейки в виде строки
  def getCellValue(row: org.apache.poi.ss.usermodel.Row, col: Int): String = {
    val cell = row.getCell(col)
    if (cell == null) null else dataFormatter.formatCellValue(cell)
  }

  // Словари для хранения данных
  val dct = mutable.Map[String, mutable.Map[String, Any]]()
  val dctRows = mutable.Map[String, mutable.Map[String, Any]]()
  val dctCondition = mutable.Map[String, mutable.Map[String, String]]()

  // Обработка листа "тарелки"
  for (i <- 1 until sheetТарелки.getLastRowNum + 1) {
    val row = sheetТарелки.getRow(i)
    if (getCellValue(row, 6) != null) { // Проверка столбца "incriment"
      val key = getCellValue(row, 0).trim // sourceTableName
      val value = getCellValue(row, 6) // incriment
      val formatValue = getCellValue(row, 3) // sourceFieldName

      if (!dct.contains(key)) {
        dct(key) = mutable.Map(value -> formatValue, "format_date" -> 0)
      } else {
        dct(key)(value) = formatValue
      }
    }
  }

  // Обновление format_date
  for ((k, v) <- dct) {
    for ((k1, _) <- v) {
      if (k1.contains("field_time_change")) {
        if (k1.contains("field_date_change") || k1.contains("field_date_create")) {
          dct(k)("format_date") = 14
        } else {
          dct(k)("format_date") = 6
        }
      } else {
        dct(k)("format_date") = 8
      }
    }
  }

  import org.apache.poi.ss.usermodel.CellType

  // Обработка листа "штрудели"
  for (i <- 1 until sheetШтрудели.getLastRowNum + 1) {
    val row = sheetШтрудели.getRow(i)
    val cell = row.getCell(5) // Столбец max_rows
    val maxRowsValue = if (cell != null) {
      cell.getCellType match {
        case CellType.NUMERIC => cell.getNumericCellValue.toInt
        case CellType.STRING =>
          // Удаляем пробелы и пытаемся преобразовать в число
          val cleanedValue = cell.getStringCellValue.replaceAll("\\s", "")
          try {
            cleanedValue.toInt
          } catch {
            case _: NumberFormatException =>
              println(s"Предупреждение: не удалось преобразовать '$cleanedValue' в число. Используется значение по умолчанию.")
              10000000 // Значение по умолчанию
          }
        case CellType.FORMULA =>
          if (cell.getCachedFormulaResultType == CellType.NUMERIC) {
            cell.getNumericCellValue.toInt
          } else {
            10000000 // Значение по умолчанию, если формула возвращает не число
          }
        case _ => 10000000 // Значение по умолчанию для других типов
      }
    } else {
      10000000 // Значение по умолчанию, если ячейка пустая
    }
    val key = getCellValue(row, 0).toUpperCase.trim // TARGETTABLENAME
    dctRows(key) = mutable.Map("max_rows" -> maxRowsValue)
  }

  // Обработка условий
  for (i <- 1 until sheetТарелки.getLastRowNum + 1) {
    val row = sheetТарелки.getRow(i)
    if (getCellValue(row, 4) != null) { // Проверка столбца "condition"
      val key = getCellValue(row, 0).trim // sourceTableName
      val condition = s"${getCellValue(row, 3)} ${getCellValue(row, 4)} '${getCellValue(row, 5)}'"
      if (!dctCondition.contains(key)) {
        dctCondition(key) = mutable.Map(condition -> "")
      } else {
        dctCondition(key)(condition) = ""
      }
    }
  }

  // Создание запросов
  def createQuery(): List[Map[String, Any]] = {
    val result = mutable.ListBuffer[Map[String, Any]]()
    var currentTable = getCellValue(sheetТарелки.getRow(1), 0)
    var lst = mutable.ListBuffer[String]()

    for (k <- 1 until sheetТарелки.getLastRowNum + 2) {
      val row = if (k <= sheetТарелки.getLastRowNum) sheetТарелки.getRow(k) else null
      val tableName = if (row != null) getCellValue(row, 0) else null

      if (tableName == currentTable) {
        lst += getCellValue(row, 3).trim // sourceFieldName
      } else {
        val item = mutable.Map(
          "table_name" -> currentTable,
          "columns" -> lst.mkString(", "),
          "where" -> "1=1"
        )

        if (dct.contains(currentTable)) {
          item ++= dct(currentTable).map { case (k, v) => (k, v.toString) }
        }
        if (dctRows.contains(currentTable)) {
          item ++= dctRows(currentTable).map { case (k, v) => (k, v.toString) }
        }
        if (!dctRows.contains(currentTable)) {
          item("max_rows") = 10000000.toString // Преобразуем Int в String
        }
        if (dctCondition.contains(currentTable)) {
          item("where") = dctCondition(currentTable).keys.mkString(" and ")
        }

        result += item.toMap
        if (row != null) {
          currentTable = tableName
          lst = mutable.ListBuffer(getCellValue(row, 3))
        }
      }
    }

    result.toList
  }

  // Формирование результата
  val result = createQuery()

  // Вывод результатов
  println(dctCondition)
  println(result.length)

  // Запись результата в JSON-файл
  implicit val formats: Formats = DefaultFormats
  val jsonString = write(result)
  val file = new java.io.File("json_meow.json")
  val bw = new java.io.BufferedWriter(new java.io.FileWriter(file))
  bw.write(jsonString)
  bw.close()
}
