import 'dart:io';

class Generator {
  static const String CSV_LIST_SEPARATOR = '|';
  static const String ENUM_CLASS_NAME = "ErrorEnum";
  static const String FILE_SUFFIX_NAME = "dart";
  String inputFile;
  String outputDir;
  List<String> attributeList;

  Generator(String inputFile, String outputDir) {
    this.inputFile = inputFile;
    this.outputDir = outputDir;
  }

  void generate() {
    Map<String, Map<String, String>> dataMap = obtainDataMap();
    generateConstantFile(dataMap);
    generateEnumFile(dataMap);
  }

  void generateConstantFile(Map<String, Map<String, String>> dataMap) {
    for (String attribute in attributeList) {
      String className = toBigHump(attribute);
      String filePath = obtainOutputFilePath(className);
      String content = obtainConstantTemplate(className, attribute, dataMap);
      generateFile(filePath, content);
    }
  }

  void generateEnumFile(Map<String, Map<String, String>> dataMap) {
    String className = ENUM_CLASS_NAME;
    String filePath = obtainOutputFilePath(className);
    String content = obtainEnumTemplate(className, dataMap);
    generateFile(filePath, content);
  }

  Map<String, Map<String, String>> obtainDataMap() {
    var dataList = List<List<String>>();
    List<String> rowList = parseFile(inputFile);
    for (String row in rowList) {
      dataList.add(splitRowData(row));
    }
    attributeList = dataList.removeAt(0);

    var dataMap = Map<String, Map<String, String>>();
    var attributeSize = attributeList.length;
    for (var rowDataList in dataList) {
      fillList(rowDataList, attributeSize);
      String name = obtainLabelName(rowDataList[0]);
      var itemMap = Map<String, String>();
      dataMap[name] = itemMap;

      for (int index = 0; index < attributeSize; index++) {
        String attribute = attributeList[index];
        itemMap[attribute] = rowDataList[index];
      }
    }
    return dataMap;
  }

  List<String> splitRowData(String row) {
    List<String> list = List<String>();
    var rowArr = row.split(CSV_LIST_SEPARATOR);
    for (String data in rowArr) {
      list.add(data.trim());
    }
    return list;
  }

  List<String> fillList(List<String> list, int tagSize) {
    if (list.length < tagSize) {
      for (int index = 0; index < tagSize - list.length; index++) {
        list.add("");
      }
    }
    return list;
  }

  String obtainLabelName(String name) {
    String regex = "\\s+";
    return name.trim().replaceAll(RegExp(regex), "_").toUpperCase();
  }

  String obtainPropertyName(String property) {
    return toSmallHump(property);
  }

  String toBigHump(String str) {
    return toHump(str, false);
  }

  String toSmallHump(String str) {
    return toHump(str, true);
  }

  String toHump(String str, bool isSmallHump) {
    var builder = StringBuffer();
    var strArr = str.split("[ _-]");
    String subStr = "";
    for (int index = 0; index < strArr.length; index++) {
      subStr = strArr[index];
      if (index != 0 || !isSmallHump) {
        subStr = capitalize(subStr);
      }
      builder.write(subStr);
    }
    return builder.toString();
  }

  String capitalize(String str) {
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  String obtainConstantTemplate(String className, String attribute, Map<String, Map<String, String>> dataMap) {
    var builder = StringBuffer();
    builder.write("class $className {\n");
    for (MapEntry<String, Map<String, String>> data in dataMap.entries) {
      String format = "\tstatic const ${data.key} = \"${data.value[attribute]}\";\n";
      builder.write(format);
    }
    builder.write("}\r\n");
    return builder.toString();
  }

  String obtainEnumTemplate(String className, Map<String, Map<String, String>> dataMap) {
    return "class $className {\n" +
        "${obtainEnumDataList(dataMap, className)}\n" +
        "${obtainEnumProperty()}\n" +
        "${obtainEnumConstructor(className)}\n" +
        "}";
  }

  String obtainEnumDataList(Map<String, Map<String, String>> dataMap, String className) {
    var builder = StringBuffer();
    List<String> propertyList = obtainEnumPropertyList();
    var nameList = dataMap.keys.toList();
    for (int index = 0; index < nameList.length; index++) {
      String name = nameList[index];
      var itemMap = dataMap[name];
      String propertyArrString = propertyList.map((property) => "\"${itemMap[property]}\"").join(", ");
      builder.write("\tstatic const $name = ${obtainPrivateConstructorName(className)}($propertyArrString);\n");
    }
    return builder.toString();
  }

  String obtainEnumConstructor(String className) {
    var builder = StringBuffer();
    List<String> propertyList = obtainEnumPropertyList();
    String propertyArrString = propertyList.map((property) => "this.${obtainPropertyName(property)}").join(", ");
    builder.write("\tconst ${obtainPrivateConstructorName(className)}($propertyArrString);\n");
    return builder.toString();
  }

  String obtainPrivateConstructorName(String className) {
    return "$className._init";
  }

  String obtainEnumProperty() {
    var builder = StringBuffer();
    List<String> propertyList = obtainEnumPropertyList();
    for (String property in propertyList) {
      builder.write("\tfinal ${obtainPropertyName(property)};\n");
    }
    return builder.toString();
  }

  List<String> obtainEnumPropertyList() {
    var list = List<String>();
    for (int index = 1; index < attributeList.length; index++) {
      String tag = attributeList[index];
      list.add(tag);
    }
    return list;
  }

  String obtainOutputFilePath(String name) {
    return "$outputDir${Platform.pathSeparator}$name.$FILE_SUFFIX_NAME";
  }

  void generateFile(String filePath, String content) {
    try {
      File(filePath).create(recursive: true).then((file) {
        file.writeAsString(content);
      });
    } catch (e) {
      print(e);
    }
  }

  List<String> parseFile(String filePath) {
    var list = List<String>();
    try {
      File file = File(filePath);
      list = file.readAsLinesSync();
    } catch (e) {
      print(e);
    }
    return list;
  }
}

void main() {
  var currentPath = Directory.current.path;
  String inputFile = currentPath + Platform.pathSeparator + "error-code.csv";
  String outputDir = currentPath + Platform.pathSeparator + "output" + Platform.pathSeparator + "dart";
  Generator(inputFile, outputDir).generate();
}
