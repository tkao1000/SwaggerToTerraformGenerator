package com.sumologic.terraform_generator.writer

import com.sumologic.terraform_generator.objects.{ForbiddenGoTerms, ScalaSwaggerObject, ScalaSwaggerObjectArray, ScalaSwaggerTemplate, ScalaSwaggerType, ScalaTerraformEntity, TerraformSchemaTypes}

case class AcceptanceTestFileGenerator(terraform: ScalaSwaggerTemplate, mainClass: String)
  extends TerraformFileGeneratorBase(terraform: ScalaSwaggerTemplate) {
  val functionGenerator = AcceptanceTestFunctionGenerator(terraform, terraform.getAllTypesUsed().filter(_.name.toLowerCase.contains(mainClass.toLowerCase)).head)
  def generate(): String = {
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
                 |	"fmt"
                 |	"testing"
                 |  "strconv"
                 |  "strings"
                 |	"github.com/hashicorp/terraform-plugin-sdk/helper/resource"
                 |	"github.com/hashicorp/terraform-plugin-sdk/terraform"
                 |)
                 |""".stripMargin
    pre + functionGenerator.generateTestFunctionCreateBasic() + "\n" + functionGenerator.generateTestFunctionCreate() + "\n" + functionGenerator.generateTestFunctionDestroy() +
      functionGenerator.generateTestFunctionExists() + "\n" + functionGenerator.generateTestFunctionUpdate() + "\n" + functionGenerator.generateTestImportFunction() + "\n" +
      functionGenerator.generateTestCreateResource() + "\n" + functionGenerator.generateTestUpdateResource() + "\n" + functionGenerator.generateTestResourceAttributes()
  }
}

case class AcceptanceTestFunctionGenerator(sumoSwaggerTemplate: ScalaSwaggerTemplate, mainClass: ScalaSwaggerType)
  extends ScalaTerraformEntity
    with AcceptanceTestGeneratorHelper {
  val className = mainClass.name
  val objName = lowerCaseFirstLetter(className)
  val resourceProps = sumoSwaggerTemplate.getAllTypesUsed().head

  def generateTestFunctionCreateBasic(): String = {
    val setters = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        s"""test${prop.getName.capitalize} := ${getTestValue(prop)}"""
    }.mkString("\n  ")

    val testNames = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        s"""test${prop.getName.capitalize}"""
    }.mkString(", ")

    s"""func TestAccSumologic${className}_basic(t *testing.T) {
       |	var $objName $className
       |	$setters
       |
       |	resource.Test(t, resource.TestCase{
       |		PreCheck:     func() { testAccPreCheck(t) },
       |		Providers:    testAccProviders,
       |		CheckDestroy: testAccCheck${className}Destroy($objName),
       |		Steps: []resource.TestStep{
       |			{
       |				Config: testAccCheckSumologic${className}ConfigImported($testNames),
       |			},
       |			{
       |				ResourceName:      "sumologic_${removeCamelCase(objName)}.foo",
       |				ImportState:       true,
       |				ImportStateVerify: true,
       |			},
       |		},
       |	})
       |}""".stripMargin
  }

  def generateTestFunctionCreate(): String = {
    val setters = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        s"""test${prop.getName.capitalize} := ${getTestValue(prop)}"""
    }.mkString("\n  ")

    val testNames = "(" + filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        s"""test${prop.getName.capitalize}"""
    }.mkString(", ")

    val checkAttr = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        prop.getType.name match {
          case "bool" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", strconv.FormatBool(test${prop.getName.capitalize})),"""
          case "int" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", strconv.Itoa(test${prop.getName.capitalize})),"""
          case "array" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}.0", strings.Replace(test${prop.getName.capitalize}[0], "\\"", "", 2)),"""
          case _ =>
            if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
              s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}.0", strings.Replace(test${prop.getName.capitalize}[0], "\\"", "", 2)),"""
            } else {
              s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", test${prop.getName.capitalize}),"""
            }
        }
    }.mkString("\n          ")

    s"""func TestAcc${className}_create(t *testing.T) {
       |  var ${objName} ${className}
       |  $setters
       |  resource.Test(t, resource.TestCase{
       |    PreCheck: func() { testAccPreCheck(t) },
       |    Providers:    testAccProviders,
       |    CheckDestroy: testAccCheck${className}Destroy(${objName}),
       |    Steps: []resource.TestStep{
       |      {
       |        Config: testAccSumologic${className}$testNames),
       |        Check: resource.ComposeTestCheckFunc(
       |          testAccCheck${className}Exists("sumologic_${{removeCamelCase(objName)}}.test", &${objName}, t),
       |          testAccCheck${className}Attributes("sumologic_${{removeCamelCase(objName)}}.test"),
       |          $checkAttr
       |        ),
       |      },
       |    },
       |  })
       |}""".stripMargin
  }

  def generateTestFunctionDestroy(): String = {
    s"""
       |func testAccCheck${className}Destroy(${objName} ${className}) resource.TestCheckFunc {
       |	return func(s *terraform.State) error {
       |		client := testAccProvider.Meta().(*Client)
       |    for _, r := range s.RootModule().Resources {
       |      id := r.Primary.ID
       |		  u, err := client.Get${className}(id)
       |		  if err != nil {
       |        return fmt.Errorf("Encountered an error: " + err.Error())
       |		  }
       |      if u != nil {
       |        return fmt.Errorf("${className} still exists")
       |      }
       |    }
       |		return nil
       |	}
       |}
       |""".stripMargin
  }

  def generateTestFunctionExists(): String = {
    s"""func testAccCheck${className}Exists(name string, ${objName} *${className}, t *testing.T) resource.TestCheckFunc {
       |	return func(s *terraform.State) error {
       |		rs, ok := s.RootModule().Resources[name]
       |		if !ok {
       |      //need this so that we don't get an unused import error for strconv in some cases
       |			return fmt.Errorf("Error = %s. ${className} not found: %s", strconv.FormatBool(ok), name)
       |		}
       |
       |    //need this so that we don't get an unused import error for strings in some cases
       |		if strings.EqualFold(rs.Primary.ID, "") {
       |			return fmt.Errorf("${className} ID is not set")
       |		}
       |
       |		id := rs.Primary.ID
       |		c := testAccProvider.Meta().(*Client)
       |		new${className}, err := c.Get${className}(id)
       |		if err != nil {
       |			return fmt.Errorf("${className} %s not found", id)
       |		}
       |		${objName} = new${className}
       |		return nil
       |	}
       |}""".stripMargin
  }

  def generateTestFunctionUpdate(): String = {
    val testArguments = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        s"""test${prop.getName.capitalize} := ${getTestValue(prop)}"""
    }.mkString("\n  ")
    val testUpdateArguments = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        s"""testUpdated${prop.getName.capitalize} := ${getTestValue(prop, true)}"""
    }.mkString("\n  ")
    val argList = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop => s"""test${prop.getName().capitalize}"""
    }.mkString(", ")
    val checkAttr = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        prop.getType.name match {
          case "bool" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", strconv.FormatBool(test${prop.getName.capitalize})),"""
          case "int" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", strconv.Itoa(test${prop.getName.capitalize})),"""
          case "array" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}.0", strings.Replace(test${prop.getName.capitalize}[0], "\\"", "", 2)),"""
          case _ =>
            if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
              s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}.0", strings.Replace(test${prop.getName.capitalize}[0], "\\"", "", 2)),"""
            } else {
              s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", test${prop.getName.capitalize}),"""
            }
        }
    }.mkString("\n          ")
    val updateArgList = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop => s"""testUpdated${prop.getName().capitalize}"""
    }.mkString(", ")
    val checkUpdateAttr = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        prop.getType.name match {
          case "bool" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", strconv.FormatBool(testUpdated${prop.getName.capitalize})),"""
          case "int" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", strconv.Itoa(testUpdated${prop.getName.capitalize})),"""
          case "array" =>
            s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}.0", strings.Replace(testUpdated${prop.getName.capitalize}[0], "\\"", "", 2)),"""
          case _ =>
            if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
              s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}.0", strings.Replace(testUpdated${prop.getName.capitalize}[0], "\\"", "", 2)),"""
            } else {
              s"""resource.TestCheckResourceAttr("sumologic_${removeCamelCase(objName)}.test", "${removeCamelCase(prop.getName())}", testUpdated${prop.getName.capitalize}),"""
            }
        }
    }.mkString("\n          ")

    s"""
       |func TestAcc${className}_update(t *testing.T) {
       |  var ${objName} ${className}
       |  $testArguments
       |
       |  $testUpdateArguments
       |
       |	resource.Test(t, resource.TestCase{
       |		PreCheck: func() { testAccPreCheck(t) },
       |		Providers:    testAccProviders,
       |		CheckDestroy: testAccCheck${className}Destroy($objName),
       |		Steps: []resource.TestStep{
       |			{
       |				Config: testAccSumologic${className}($argList),
       |				Check: resource.ComposeTestCheckFunc(
       |					testAccCheck${className}Exists("sumologic_${removeCamelCase(objName)}.test", &$objName, t),
       |					testAccCheck${className}Attributes("sumologic_${removeCamelCase(objName)}.test"),
       |          $checkAttr
       |				),
       |			},
       |			{
       |				Config: testAccSumologic${className}Update($updateArgList),
       |				Check: resource.ComposeTestCheckFunc(
       |					$checkUpdateAttr
       |				),
       |			},
       |		},
       |	})
       |}""".stripMargin
  }

  def generateTestImportFunction(): String = {
    val propArgs = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        val name = if (ForbiddenGoTerms.forbidden.contains(prop.getName.toLowerCase)) {
          prop.getName + "_field"
        } else {
          prop.getName()
        }
        if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
          s"""${name} ${TerraformSchemaTypes.swaggerTypeToGoType("array")}"""
        } else {
          s"""${name} ${TerraformSchemaTypes.swaggerTypeToGoType(prop.getType.name.toLowerCase)}"""
        }
    }.mkString(", ")
    val terraformArgs = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
          s"""${removeCamelCase(prop.getName())} = ${TerraformSchemaTypes.swaggerTypeToPlaceholder("array")}"""
        } else {
          s"""${removeCamelCase(prop.getName())} = ${TerraformSchemaTypes.swaggerTypeToPlaceholder(prop.getType.name)}"""
        }
    }.mkString("\n      ")
    val propList = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        if (ForbiddenGoTerms.forbidden.contains(prop.getName.toLowerCase)) {
          prop.getName + "_field"
        } else {
          prop.getName()
        }
    }.mkString(", ")
    s"""func testAccCheckSumologic${className}ConfigImported($propArgs) string {
       |	return fmt.Sprintf(`
       |resource "sumologic_${removeCamelCase(objName)}" "foo" {
       |      $terraformArgs
       |}
       |`, $propList)
       |}""".stripMargin
  }

  def generateTestCreateResource(): String = {
    val propArgs = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        val name = if (ForbiddenGoTerms.forbidden.contains(prop.getName.toLowerCase)) {
          prop.getName + "_field"
        } else {
          prop.getName()
        }
        if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
          s"""${name} ${TerraformSchemaTypes.swaggerTypeToGoType("array")}"""
        } else {
          s"""${name} ${TerraformSchemaTypes.swaggerTypeToGoType(prop.getType.name.toLowerCase)}"""
        }
    }.mkString(", ")
    val terraformArgs = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
          s"""${removeCamelCase(prop.getName())} = ${TerraformSchemaTypes.swaggerTypeToPlaceholder("array")}"""
        } else {
          s"""${removeCamelCase(prop.getName())} = ${TerraformSchemaTypes.swaggerTypeToPlaceholder(prop.getType.name)}"""
        }
    }.mkString("\n    ")
    val propList = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        if (ForbiddenGoTerms.forbidden.contains(prop.getName.toLowerCase)) {
          prop.getName + "_field"
        } else {
          prop.getName()
        }
    }.mkString(", ")

    s"""
       |func testAccSumologic${className}($propArgs) string {
       |	return fmt.Sprintf(`
       |resource "sumologic_${removeCamelCase(objName)}" "test" {
       |    $terraformArgs
       |}
       |`, $propList)
       |}""".stripMargin
  }

  def generateTestUpdateResource(): String = {
    val propArgs = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        val name = if (ForbiddenGoTerms.forbidden.contains(prop.getName.toLowerCase)) {
          prop.getName + "_field"
        } else {
          prop.getName()
        }
        if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
          s"""${name} ${TerraformSchemaTypes.swaggerTypeToGoType("array")}"""
        } else {
          s"""${name} ${TerraformSchemaTypes.swaggerTypeToGoType(prop.getType.name.toLowerCase)}"""
        }
    }.mkString(", ")
    val terraformArgs = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        if (prop.isInstanceOf[ScalaSwaggerObjectArray]) {
          s"""${removeCamelCase(prop.getName())} = ${TerraformSchemaTypes.swaggerTypeToPlaceholder("array")}"""
        } else {
          s"""${removeCamelCase(prop.getName())} = ${TerraformSchemaTypes.swaggerTypeToPlaceholder(prop.getType.name)}"""
        }
    }.mkString("\n      ")
    val propList = filterProps(resourceProps.props, List("id", "roleids")).map {
      prop =>
        if (ForbiddenGoTerms.forbidden.contains(prop.getName.toLowerCase)) {
          prop.getName + "_field"
        } else {
          prop.getName()
        }
    }.mkString(", ")

    s"""
       |func testAccSumologic${className}Update($propArgs) string {
       |	return fmt.Sprintf(`
       |resource "sumologic_${removeCamelCase(objName)}" "test" {
       |      $terraformArgs
       |}
       |`, $propList)
       |}""".stripMargin
  }

  def generateTestResourceAttributes(): String = {
    val checkResourceAttr = filterProps(resourceProps.props, List("id", "roleids", "capabilities")).map {
      prop =>
        s"""resource.TestCheckResourceAttrSet(name, "${removeCamelCase(prop.getName())}"),"""
    }.mkString("\n        ")

    s"""
       |func testAccCheck${className}Attributes(name string) resource.TestCheckFunc {
       |  return func(s *terraform.State) error {
       |      f := resource.ComposeTestCheckFunc(
       |        $checkResourceAttr
       |      )
       |      return f(s)
       |   }
       |}""".stripMargin
  }

  private def filterProps(props: List[ScalaSwaggerObject], filterOut: List[String]): List[ScalaSwaggerObject] = {
    props.filterNot {
      prop => filterOut.contains(prop.getName.toLowerCase)
    }
  }
}
