package main

import (
	"bufio"
	"container/list"
	"fmt"
	"io"
	"os"
	"path"
	"regexp"
	"strings"
)

const (
	CSV_LIST_SEPARATOR = "|"
	FILE_SUFFIX_NAME   = "go"
	ENUM_CLASS_NAME    = "ErrorEnum"
)

type Generator struct {
	inputFile string
	outputDir string
}

func (generator Generator) Generate() {
	dataMap, attributeList := obtainMapData(generator.inputFile)
	generator.GenerateConstantFile(dataMap, attributeList)
	generator.GenerateEnumFile(dataMap, attributeList)
}

func (generator Generator) GenerateConstantFile(dataMap map[string]map[string]string, attributeList []string) {
	outputDir := generator.outputDir
	for _, attribute := range attributeList {
		className := toBigHump(attribute)
		filePath := obtainOutputFilePath(outputDir, className)
		content := obtainConstantTemplate(attribute, dataMap)
		generateFile(filePath, content)
	}
}

func (generator Generator) GenerateEnumFile(dataMap map[string]map[string]string, attributeList []string) {
	name := ENUM_CLASS_NAME
	filePath := obtainOutputFilePath(generator.outputDir, name)
	content := obtainEnumTemplate(attributeList, dataMap)
	generateFile(filePath, content)
}

func obtainMapData(inputFile string) (map[string]map[string]string, []string) {
	dataList := list.New()
	rowList := parseFile(inputFile)
	for _, row := range rowList {
		dataList.PushBack(splitRowData(row))
	}
	attributeList := dataList.Front().Value.([]string)

	attributeSize := len(attributeList)
	dataMap := make(map[string]map[string]string)
	for data := dataList.Front(); data != nil; data = data.Next() {
		rowDataList := data.Value.([]string)
		rowDataList = fillList(rowDataList, attributeSize)
		name := obtainLabelName(rowDataList[0])
		itemMap := make(map[string]string)
		dataMap[name] = itemMap

		for index := 0; index < attributeSize; index++ {
			attribute := attributeList[index]
			itemMap[attribute] = rowDataList[index]
		}
	}
	return dataMap, attributeList
}

func splitRowData(row string) []string {
	var dataList []string
	rowArr := strings.Split(string(row), CSV_LIST_SEPARATOR)
	for _, data := range rowArr {
		dataList = append(dataList, data)
	}
	return dataList
}

func obtainLabelName(name string) string {
	name = strings.Trim(name, " ")
	name = strings.ToUpper(name)
	regex, _ := regexp.Compile("\\s+")
	return regex.ReplaceAllString(name, "_")
}

func fillList(list []string, size int) []string {
	if len(list) < size {
		for index := 0; index < size-len(list); index++ {
			list = append(list, "")
		}
	}
	return list
}

func obtainPropertyName(property string) string {
	return toSmallHump(property)
}

func toBigHump(str string) string {
	return toHump(str, false)
}

func toSmallHump(str string) string {
	return toHump(str, true)
}

func toHump(str string, isSmallHump bool) string {
	var builder strings.Builder
	strArr := strings.Split(str, "[ _-]")
	for index := 0; index < len(strArr); index++ {
		subStr := strArr[index]
		if index != 0 || !isSmallHump {
			subStr = capitalize(subStr)
		}
		builder.WriteString(subStr)
	}
	return builder.String()
}

func capitalize(str string) string {
	firstStr := string(str[0:1])
	firstStr = strings.ToUpper(firstStr)
	return firstStr + string(str[1:])
}

func obtainEnumTemplate(attributeList []string, dataMap map[string]map[string]string) string {
	propertyList := obtainEnumPropertyList(attributeList)
	return fmt.Sprintf("%s\n%s", obtainEnumDataStruct(propertyList), obtainEnumDataList(propertyList, dataMap))
}

func obtainEnumDataStruct(propertyList []string) string {
	type Data struct {
		name string
	}
	var builder strings.Builder
	builder.WriteString("type Data struct {\n")
	for _, property := range propertyList {
		builder.WriteString(fmt.Sprintf("\t%s string\n", property))
	}
	builder.WriteString("}\r\n")
	return builder.String()
}

func obtainEnumDataList(propertyList []string, dataMap map[string]map[string]string) string {
	var nameList []string
	for key := range dataMap {
		nameList = append(nameList, key)
	}
	var builder strings.Builder
	builder.WriteString("var (\n")
	for _, name := range nameList {
		var propertyArr []string
		itemMap := dataMap[name]
		for _, property := range propertyList {
			propertyArr = append(propertyArr, fmt.Sprintf("\"%s\"", itemMap[property]))
		}
		propertyArrString := strings.Join(propertyArr, ", ")
		builder.WriteString(fmt.Sprintf("\t%s = Data{%s}\n", name, propertyArrString))
	}
	builder.WriteString(")\r\n")
	return builder.String()
}

func obtainEnumPropertyList(attributeList []string) []string {
	return attributeList
}

func obtainConstantTemplate(attribute string, dataMap map[string]map[string]string) string {
	var builder strings.Builder
	builder.WriteString("const (\n")
	for itemName, itemMap := range dataMap {
		format := fmt.Sprintf("\t%s = \"%s\"\n", itemName, itemMap[attribute])
		builder.WriteString(format)
	}
	builder.WriteString(")\r\n")
	return builder.String()
}

func obtainOutputFilePath(outputDir string, name string) string {
	preGenerateDir(outputDir)
	return fmt.Sprintf("%s%c%s.%s", outputDir, os.PathSeparator, name, FILE_SUFFIX_NAME)
}

func preGenerateDir(directory string) {
	_, err := os.Stat(directory)
	if err != nil {
		os.Mkdir(directory, 0777)
	}
}

func generateFile(outputPath string, content string) {
	file, err := os.Create(outputPath)
	if err != nil {
		fmt.Println(err)
	} else {
		_, err = file.WriteString(content)
	}
	defer file.Close()
}

func parseFile(inputFile string) []string {
	var dataList []string
	fi, err := os.Open(inputFile)
	if err != nil {
		fmt.Printf("Error: %s\n", err)
		return dataList
	}
	defer fi.Close()

	br := bufio.NewReader(fi)
	for {
		line, _, err := br.ReadLine()
		if err == io.EOF {
			break
		}
		dataList = append(dataList, string(line))
	}
	return dataList
}

func main() {
	currentDir, _ := os.Getwd()
	inputFile := path.Join(currentDir, "error-code.csv")
	outputDir := path.Join(currentDir, "output", "go")
	generator := Generator{inputFile, outputDir}
	generator.Generate()
}
