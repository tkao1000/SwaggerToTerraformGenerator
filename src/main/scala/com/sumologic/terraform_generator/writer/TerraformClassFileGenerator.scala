package com.sumologic.terraform_generator.writer

import com.sumologic.terraform_generator.objects.TerraformSupportedOperations.crud
import com.sumologic.terraform_generator.objects.{ScalaSwaggerEndpoint, ScalaSwaggerTemplate, ScalaSwaggerType, TerraformSupportedParameterTypes}

case class TerraformClassFileGenerator(terraform: ScalaSwaggerTemplate)
  extends TerraformFileGeneratorBase(terraform: ScalaSwaggerTemplate) {

  def generate(): String = {
    val typesUsed: Set[ScalaSwaggerType] = terraform.getAllTypesUsed()

    val intro = s"""// ----------------------------------------------------------------------------
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
         |  "encoding/json"
         |  "fmt"
         |)
         |""".stripMargin


    val endpointsWithChangedNames = terraform.supportedEndpoints.map {
      endpoint =>
        val name = crud.find(endpoint.endpointName.toLowerCase.contains(_))
        if (name.isDefined) {
          endpoint.copy(endpointName = name.get.toLowerCase + terraform.getMainObjectClass().name.capitalize)
        } else {
          endpoint
        }
    }

    val endpoints = endpointsWithChangedNames.map {
      endpoint: ScalaSwaggerEndpoint =>
        val bodyParams = endpoint.parameters.filter {
          _.paramType == TerraformSupportedParameterTypes.BodyParameter
        }

        if (bodyParams.size > 1 || endpoint.responses.filterNot(_.respTypeName.toLowerCase == "default").size > 1) {
          val paramsToExclude = bodyParams.filterNot {
            _.param.getName().toLowerCase == terraform.sumoSwaggerClassName.toLowerCase
          }

          val filteredEndpoint = endpoint.copy(
            parameters = endpoint.parameters diff paramsToExclude,
            responses = endpoint.responses.filter {
              _.respTypeName.toLowerCase == terraform.sumoSwaggerClassName.toLowerCase
            }
          )
          filteredEndpoint.terraformify(terraform) + "\n"
        } else {
          endpoint.terraformify(terraform) + "\n"
        }
    }.mkString("")

    val types = typesUsed.map {
      sType: ScalaSwaggerType =>
        sType.terraformify(terraform) + "\n"
    }.mkString("")

    s"// ---------- BEGIN ${terraform.sumoSwaggerClassName} ----------\n" + intro +
      "\n// ---------- ENDPOINTS ---------- \n\n" + endpoints +
      "\n// ---------- TYPES ----------\n" + types +
      "\n// ---------- END ----------\n"
  }
}
