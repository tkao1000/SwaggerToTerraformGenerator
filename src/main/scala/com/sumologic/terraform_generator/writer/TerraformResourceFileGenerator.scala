package com.sumologic.terraform_generator.writer

import com.sumologic.terraform_generator.objects.TerraformSupportedOperations.crud
import com.sumologic.terraform_generator.objects.{ScalaSwaggerEndpoint, ScalaSwaggerObject, ScalaSwaggerObjectArray, ScalaSwaggerParameter, ScalaSwaggerResponse, ScalaSwaggerTemplate, ScalaSwaggerType, ScalaTerraformEntity, TerraformSupportedParameterTypes}

case class TerraformResourceFileGenerator(terraform: ScalaSwaggerTemplate)
  extends TerraformFileGeneratorBase(terraform: ScalaSwaggerTemplate)
    with ResourceGeneratorHelper {
  def generate(): String = {
    val specialImport = if (terraform.getMainObjectClass().props.exists {
      prop => prop.getType().props.nonEmpty
    }) {
      """"github.com/hashicorp/terraform-plugin-sdk/helper/validation""""
    } else {
      ""
    }
    val pre = s"""// ----------------------------------------------------------------------------
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
                |  "log"
                |  "github.com/hashicorp/terraform-plugin-sdk/helper/schema"
                |  $specialImport
                |)
                |""".stripMargin

    val mappingSchema = terraform.getResourceFuncMappings()

    val ops: String = terraform.supportedEndpoints.map {
      endpoint: ScalaSwaggerEndpoint =>
        val gen = ResourceFunctionGenerator(endpoint, terraform.getMainObjectClass())
        gen.terraformify(terraform)
    }.mkString("\n")

    val converters = getTerraformResourceDataToObjectConverter(terraform.getMainObjectClass(), true)

    pre + "\n" + mappingSchema + "\n" + ops + "\n" +
      converters
  }
}

case class ResourceFunctionGenerator(endpoint: ScalaSwaggerEndpoint, mainClass: ScalaSwaggerType) extends ScalaTerraformEntity {
  val className: String = mainClass.name
  val objName: String = lowerCaseFirstLetter(className)

  val hasParams: Boolean = endpoint.parameters.map(_.paramType).exists { param =>
    param.contains(TerraformSupportedParameterTypes.QueryParameter) ||
        param.contains(TerraformSupportedParameterTypes.HeaderParameter)
  }

  val modelInResponse: Option[ScalaSwaggerResponse] = endpoint.responses.find {
    response =>
      if (response.respTypeOpt.isDefined) {
        response.respTypeOpt.get.name.toLowerCase.contains(objName.toLowerCase)
      } else {
        false
      }
  }
  val modelInParam: Option[ScalaSwaggerParameter] = endpoint.parameters.find {
    parameter =>
      parameter.param.getName.toLowerCase.contains(objName.toLowerCase)
  }
  val parameter: String = if (modelInResponse.isDefined) {
    modelInResponse.get.respTypeOpt.get.name
  } else if (modelInParam.isDefined) {
    modelInParam.get.param.getName()
  } else {
    ""
  }

  val requestMap: String = if (hasParams) {
    s"""requestParams := make(map[string]string)
       |	for k, v := range d.Get("${endpoint.httpMethod.toLowerCase}_request_map").(map[string]interface{}) {
       |		requestParams[k] = v.(string)
       |	}""".stripMargin
  } else {
    ""
  }

  // TODO: This is gross, generalize if possible
  def generateResourceFunctionGET(): String = {
    val setters = mainClass.props.filter(_.getName().toLowerCase != "id").map {
      prop: ScalaSwaggerObject =>
        val name = prop.getName()
        s"""d.Set("${removeCamelCase(name)}", $objName.${name.capitalize})""".stripMargin
    }.mkString("\n    ")

    val clientCall = if (!requestMap.isEmpty) {
      s"${objName}, err := c.Get${className}(id, requestParams)"
    } else {
      s"${objName}, err := c.Get${className}(id)"
    }

    s"""
       |func resourceSumologic${className}Read(d *schema.ResourceData, meta interface{}) error {
       |	c := meta.(*Client)
       |
       |  $requestMap
       |
       |	id := d.Id()
       |	$clientCall
       |
       |	if err != nil {
       |		return err
       |	}
       |
       |	if $objName == nil {
       |		log.Printf("[WARN] $className not found, removing from state: %v - %v", id, err)
       |		d.SetId("")
       |		return nil
       |	}
       |
       |	$setters
       |
       |	return nil
       |}""".stripMargin
  }

  // TODO: This is gross, generalize if possible
  def generateResourceFunctionDELETE(): String = {
    val clientCall = if (!requestMap.isEmpty) {
      s"c.Delete${className}(d.Id(), requestParams)"
    } else {
      s"c.Delete${className}(d.Id())"
    }

    s"""func resourceSumologic${className}Delete(d *schema.ResourceData, meta interface{}) error {
       |  c := meta.(*Client)
       |
       |  $requestMap
       |
       |  return $clientCall
       |}""".stripMargin

  }

  // TODO: This is gross, generalize if possible
  def generateResourceFunctionUPDATE(): String = {
    val lowerCaseName = parameter.substring(0, 1).toLowerCase() + parameter.substring(1)

    val clientCall = if (!requestMap.isEmpty) {
      s"err := c.Update${className}($lowerCaseName, requestParams)"
    } else {
      s"err := c.Update${className}($lowerCaseName)"
    }

    s"""
       |func resourceSumologic${className}Update(d *schema.ResourceData, meta interface{}) error {
       |	c := meta.(*Client)
       |
       |  $requestMap
       |
       |	$lowerCaseName := resourceTo${className}(d)
       |
       |	$clientCall
       |
       |	if err != nil {
       |		return err
       |	}
       |
       |	return resourceSumologic${className}Read(d, meta)
       |}""".stripMargin
  }

  // TODO: This is gross, generalize if possible
  def generateResourceFunctionCREATE(): String = {
    val lowerCaseName = parameter.substring(0, 1).toLowerCase() + parameter.substring(1)

    val clientCall = if (!requestMap.isEmpty) {
      s"id, err := c.Create${className}($lowerCaseName, requestParams)"
    } else {
      s"id, err := c.Create${className}($lowerCaseName)"
    }

    s"""
       |func resourceSumologic${className}Create(d *schema.ResourceData, meta interface{}) error {
       |	c := meta.(*Client)
       |
       |  $requestMap
       |
       |	if d.Id() == "" {
       |		$lowerCaseName := resourceTo${className}(d)
       |		$clientCall
       |
       |		if err != nil {
       |			return err
       |		}
       |
       |		d.SetId(id)
       |	}
       |
       |	return resourceSumologic${className}Read(d, meta)
       |}""".stripMargin

  }

  // TODO: This is gross, generalize if possible
  def generateResourceFunctionEXISTS(): String = {
    s"""
       |func resourceSumologic${className}Exists(d *schema.ResourceData, meta interface{}) error {
       |	c := meta.(*Client)
       |
       |	_, err := c.Get${className}(d.Id())
       |	if err != nil {
       |		return err
       |	}
       |
       |	return nil
       |}""".stripMargin
  }

  // TODO: This is gross, generalize if possible
  override def terraformify(baseTemplate: ScalaSwaggerTemplate): String = {

    crud.find {
      op =>
        endpoint.endpointName.toLowerCase.startsWith(op.toLowerCase) // || endpoint.endpointName.toLowerCase.contains("get")
    } match {
      case Some(opName) =>
        this.getClass.getMethod("generateResourceFunction" + opName.toUpperCase()).invoke(this).toString
      case None => ""
    }
  }
}

