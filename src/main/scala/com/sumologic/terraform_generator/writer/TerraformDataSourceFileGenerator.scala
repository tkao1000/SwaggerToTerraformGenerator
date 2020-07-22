package com.sumologic.terraform_generator.writer

import com.sumologic.terraform_generator.objects.{ScalaSwaggerTemplate, ScalaSwaggerType, ScalaTerraformEntity}

case class TerraformDataSourceFileGenerator(terraform: ScalaSwaggerTemplate)
  extends TerraformFileGeneratorBase(terraform: ScalaSwaggerTemplate)
    with DataSourceGeneratorHelper {

  def generate(): String = {
    val pre = """// ----------------------------------------------------------------------------
                |//
                |//     ***     AUTO GENERATED CODE    ***    AUTO GENERATED CODE     ***
                |//
                |// ----------------------------------------------------------------------------
                |//
                |//     This file is automatically generated by Sumo Logic and manual
                |//     changes will be clobbered when the file is regenerated. Do not submit
                |//     changes to this file.
                |//
                |// ----------------------------------------------------------------------------
                |package sumologic
                |
                |import (
                |  "errors"
                |  "fmt"
                |  "github.com/hashicorp/terraform-plugin-sdk/helper/schema"
                |  "log"
                |)
                |""".stripMargin

    val dataSourceFunction = DataSourceFunctionGenerator(terraform.getMainObjectClass())
    pre + terraform.getDataSourceFuncMappings() +
      dataSourceFunction.terraformify(terraform) +
      getTerraformObjectToResourceDataConverter(terraform.getMainObjectClass())
  }
}


// FIXME: This class should not extend ScalaTerraformEntity as it doesn't make any sense.
case class DataSourceFunctionGenerator(mainClass: ScalaSwaggerType)
  extends ScalaTerraformEntity
    with DataSourceGeneratorHelper {

  def getTerraformDataResourceSetters(propName: String, objName: String): String = {
    s"""resourceData.Set("$propName", $objName.$propName)""".stripMargin
  }

  override def terraformify(baseTemplate: ScalaSwaggerTemplate): String = {
    val className = mainClass.name
    val objName = lowerCaseFirstLetter(className)

    val setter = getTerraformObjectToResourceDataConverterFuncCall(mainClass)
    s"""
       |func dataSourceSumologic${className}Get(resourceData *schema.ResourceData, meta interface{}) error {
       |  client := meta.(*Client)
       |
       |  var $objName *${className}
       |  var err error
       |  if id, ok := resourceData.GetOk("id"); ok {
       |    $objName, err = client.get${className}(id.(string))
       |    if err != nil {
       |      return fmt.Errorf("SumologicTerraformError: ${className} with id %s not found: %v", id, err)
       |    }
       |  } else {
       |      return errors.New("SumologicTerraformError: ${className} object Id is required")
       |    }
       |
       |  resourceData.SetId($objName.id)
       |  $setter
       |
       |  log.Printf("[DEBUG] SumologicTerraformDebug DataSource ${className} : retrieved %v", $objName)
       |  return nil
       |}""".stripMargin
  }
}
