import java.io.*
import java.util.*

class Generator(private val inputFile: String, private val outputDir: String) {
    private val CSV_LIST_SEPARATOR = "|"
    private val ENUM_CLASS_NAME = "ErrorEnum"
    private val FILE_SUFFIX_NAME = "kt"
    private lateinit var attributeList: List<String>

    fun generate() {
        val dataMap = obtainDataMap()
        generateConstantFile(dataMap)
        generateEnumFile(dataMap)
    }

    private fun generateConstantFile(dataMap: Map<String, Map<String, String>>) {
        for (attribute in attributeList) {
            val className = toBigHump(attribute)
            val filePath = obtainOutputFilePath(className)
            val content = obtainConstantTemplate(className, attribute, dataMap)
            generateFile(filePath, content)
        }
    }

    private fun generateEnumFile(dataMap: Map<String, Map<String, String>>) {
        val className = ENUM_CLASS_NAME
        val filePath = obtainOutputFilePath(className)
        val content = obtainEnumTemplate(className, dataMap)
        generateFile(filePath, content)
    }

    private fun obtainDataMap(): Map<String, Map<String, String>> {
        val dataList = mutableListOf<List<String>>()
        parseFile(inputFile).forEach { 
            dataList.add(splitRowData(it)) 
        }
        attributeList = dataList.removeAt(0)
        
        val dataMap = mutableMapOf<String, MutableMap<String, String>>()
        val attributeSize = attributeList.size
        for (itemList in dataList) {
            val rowDataList = fillList(itemList.toMutableList(), attributeSize)
            val name = obtainLabelName(rowDataList[0])
            val itemMap = mutableMapOf<String, String>()
            dataMap[name] = itemMap

            for (index in 0 until attributeSize) {
                val attribute = attributeList[index]
                itemMap[attribute] = rowDataList[index]
            }
        }
        return dataMap
    }

    private fun splitRowData(row: String): List<String>  {
        return row.split(CSV_LIST_SEPARATOR).map{ it.trim() }
    }

    private fun fillList(list: MutableList<String>, size: Int): List<String> {
        if (list.size < size) {
            for (index in 0 until size - list.size) {
                list.add("")
            }
        }
        return list
    }

    private fun obtainLabelName(name: String): String {
        val regex = "\\s+"
        return name.trim { it <= ' ' }.replace(regex.toRegex(), "_").toUpperCase()
    }

    private fun obtainPropertyName(property: String): String {
        return toSmallHump(property)
    }

    private fun toBigHump(str: String): String {
        return toHump(str, false)
    }

    private fun toSmallHump(str: String): String {
        return toHump(str, true)
    }

    private fun toHump(str: String, isSmallHump: Boolean): String {
        val builder = StringBuilder()
        val strArr = str.split("[ _-]".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        for (index in strArr.indices) {
            var subStr = strArr[index]
            if (index != 0 || !isSmallHump) {
                subStr = capitalize(subStr)
            }
            builder.append(subStr)
        }
        return builder.toString()
    }

    fun capitalize(str: String): String {
        return str.substring(0, 1).toUpperCase() + str.substring(1)
    }

    private fun obtainConstantTemplate(className: String, attribute:String, dataMap: Map<String, Map<String, String>>): String {
        val builder = StringBuilder()
        builder.append("object $className {\n")
        dataMap.forEach { builder.append("\tval ${it.key} = \"${it.value[attribute]}\"\n") }
        builder.append("}\r\n")
        return builder.toString()
    }

    private fun obtainEnumTemplate(className: String, dataMap: Map<String, Map<String, String>>): String {
        return "enum class $className(${obtainEnumProperty()}) {\n${obtainEnumDataList(dataMap)}}"
    }

    private fun obtainEnumDataList(dataMap: Map<String, Map<String, String>>): String {
        val builder = StringBuilder()
        val propertyList = obtainEnumPropertyList()
        val nameList = ArrayList(dataMap.keys)
        for (index in nameList.indices) {
            val separator = if (index < nameList.size - 1) "," else ""
            val name = nameList[index]
            val itemMap = dataMap[name] ?: mapOf()
            val propertyArrString = propertyList.joinToString(", ", "(", ")") { property -> String.format("\"%s\"", itemMap[property]) }

            builder.append("\t")
                    .append(name)
                    .append(propertyArrString)
                    .append(separator)
                    .append("\n")
        }
        return builder.toString()
    }

    private fun obtainEnumProperty(): String {
        return obtainEnumPropertyList().joinToString(separator = ", ") { property -> "val ${obtainPropertyName(property)}: String" }
    }

    private fun obtainEnumPropertyList(): List<String> {
        return attributeList
    }

    private fun obtainOutputFilePath(name: String): String {
        preGenerateDir(outputDir)
        return String.format("%s%s%s.%s", outputDir, File.separator, name, FILE_SUFFIX_NAME)
    }

    private fun preGenerateDir(directory: String) {
        val file = File(directory)
        if (!file.exists() && !file.isDirectory) {
            file.mkdirs()
        }
    }

    @Throws(IOException::class)
    private fun preGenerateFile(filePath: String): File {
        val file = File(filePath)
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

    private fun generateFile(filePath: String, content: String) {
        var writer: PrintWriter? = null
        try {
            val file = preGenerateFile(filePath)
            writer = PrintWriter(FileOutputStream(file))
            writer.write(content)
            writer.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            writer?.close()
        }
    }

    private fun parseFile(filePath: String): List<String> {
        return File(filePath).readLines()
    }

}

fun main() {
    val currentPath = File("").absolutePath
    val inputFile = currentPath + File.separator + "error-code.csv"
    val outputDir = currentPath + File.separator + "output" + File.separator + "kotlin"
    Generator(inputFile, outputDir).generate()
}