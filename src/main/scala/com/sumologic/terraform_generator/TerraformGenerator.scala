package com.sumologic.terraform_generator

import java.io.{BufferedWriter, File, FileWriter}

import com.sumologic.terraform_generator.objects.SumoSwaggerTemplate
import com.sumologic.terraform_generator.utils.SumoTerraformUtils
import com.sumologic.terraform_generator.writer.{SumoDocsGenerator, SumoTerraformClassFileGenerator, SumoTerraformResourceFileGenerator, SumoTestGenerator}
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions


object TerraformGenerator extends TerraformGeneratorHelper {

  val targetDirectory = "./target/"
  val resourcesDirectory: String = targetDirectory + "resources/"

  def main(args: Array[String]): Unit = {
    val inputFile = args(0)
    val types = if (args.size > 1) {
      args.drop(1).toList
    } else {
      List.empty[String]
    }

    val parseOpts = new ParseOptions()
    parseOpts.setResolve(true)
    parseOpts.setResolveCombinators(true)

    ensureDirectories()

    val swagger = new OpenAPIParser().readLocation(inputFile, null, parseOpts)
    val f = new File(targetDirectory + "openapi_schema.txt")
    val bw = new BufferedWriter(new FileWriter(f))
    bw.write(swagger.getOpenAPI.toString)
    bw.close()
    if (!types.isEmpty) {
      types.foreach {
        baseType =>
          val terraform = SumoTerraformUtils.processClass(swagger.getOpenAPI, baseType)
          writeFiles(terraform, baseType)
      }
    } else {
      val terraforms = SumoTerraformUtils.processAllClasses(swagger.getOpenAPI)
      terraforms.foreach {
        case (terraform: SumoSwaggerTemplate, baseType: String) =>
          writeFiles(terraform, baseType)
      }
    }
  }

  def writeFiles(sumoSwaggerTemplate: SumoSwaggerTemplate, baseType: String) = {
    val genSumoClass = SumoTerraformClassFileGenerator(sumoSwaggerTemplate)
    val terraformTypeName = removeCamelCase(baseType)

    genSumoClass.writeToFile(resourcesDirectory + s"sumologic_$terraformTypeName.go")

    val genResource = SumoTerraformResourceFileGenerator(sumoSwaggerTemplate)
    genResource.writeToFile(resourcesDirectory + s"resource_sumologic_$terraformTypeName.go")

    val genTest = SumoTestGenerator(sumoSwaggerTemplate, baseType)
    genTest.writeToFile(resourcesDirectory + s"resource_sumologic_${terraformTypeName}_test.go")

    val genDocs = SumoDocsGenerator(sumoSwaggerTemplate, baseType)
    genDocs.writeToFile(resourcesDirectory + s"$terraformTypeName.html.markdown")
    //val genDataSource = SumoTerraformDataSourceFileGenerator(sumoSwaggerTemplate)
    //genDataSource.writeToFile(s"data_source_sumologic_${baseType}.go")

    //val genProvider = SumoProviderGenerator(sumoSwaggerTemplate)
    //genProvider.writeToFile("provider.go")
  }

  def ensureDirectories(): Unit = {
    val directory = new File(targetDirectory)
    if (!directory.exists) {
      directory.mkdir
    }
    val resourcesDirectoryFolder = new File(resourcesDirectory)
    if (!resourcesDirectoryFolder.exists) {
      resourcesDirectoryFolder.mkdir
    }
  }
}
