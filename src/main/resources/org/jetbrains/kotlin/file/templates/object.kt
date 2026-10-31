<#assign licenseFirst = "/*">
<#assign licensePrefix = " * ">
<#assign licenseLast = " */">
<#if (project.licensePath)?? && project.licensePath != "">
<#include "${project.licensePath}">
</#if>
<#if package?? && package != "">
package ${package}

</#if>
/*
 * Author: ${user!"User"}
 * Created on: ${date!""}
 */

object ${name!"NewObject"} {

}