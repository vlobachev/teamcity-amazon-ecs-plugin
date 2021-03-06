/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.ecs.web

import jetbrains.buildServer.clouds.ecs.apiConnector.EcsApiConnectorImpl
import jetbrains.buildServer.clouds.ecs.apiConnector.EcsTaskDefinition
import jetbrains.buildServer.clouds.ecs.toAwsCredentials
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.internal.PluginPropertiesUtil
import jetbrains.buildServer.util.amazon.AWSCommonParams
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private const val ECS_TASK_DEFS_HTML = "ecsTaskDefs.html"

class EcsTaskDefinitionChooserController(private val pluginDescriptor: PluginDescriptor,
                                  web: WebControllerManager) : BaseController() {
    val url = pluginDescriptor.getPluginResourcesPath(ECS_TASK_DEFS_HTML)

    init {
        web.registerController(url, this)
    }

    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        val launchType = request.getParameter("launchType")
        val propsBean = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true)
        val props = propsBean.properties

        val modelAndView = ModelAndView(pluginDescriptor.getPluginResourcesPath("taskDefs.jsp"))
        try {
            val api = EcsApiConnectorImpl(props.toAwsCredentials(), AWSCommonParams.getRegionName(props))
            val sortedTasDefs = api.listTaskDefinitions()
                    .mapNotNull { taskDefArn -> api.describeTaskDefinition(taskDefArn) }
                    .filter { taskDef -> taskDef.isCompatibleWithLaunchType(launchType) }
                    .sortedBy { taskDef -> taskDef.displayName }
            modelAndView.model["taskDefs"] = sortedTasDefs
            modelAndView.model["error"] = ""
        } catch (ex: Exception){
            modelAndView.model["taskDefs"] = emptyList<EcsTaskDefinition>()
            modelAndView.model["error"] = ex.localizedMessage
        }
        return modelAndView
    }
}