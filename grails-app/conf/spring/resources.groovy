import org.grails.solr.SolrRunner
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH
import grails.util.GrailsUtil
import grails.util.Environment
import org.springframework.beans.propertyeditors.CustomNumberEditor
import java.text.DecimalFormatSymbols
import java.text.DecimalFormat
import org.springframework.beans.PropertyEditorRegistry
import org.springframework.beans.PropertyEditorRegistrar
import grails.util.BuildSettingsHolder
import org.codehaus.groovy.grails.commons.ConfigurationHolder

beans = {

switch (Environment.current) {
    case Environment.TEST:
      // NOTE: the buiold settings are not available in production mode
      def settings = BuildSettingsHolder.getSettings()
      solrRunner(SolrRunner) {
        solrService = ref("solrService")
        solrPluginDir = settings.baseDir.absolutePath
        solrHomeDir = "$settings.projectTargetDir/solr-home-during-tests"
        solrConfigSourceDir = "$settings.baseDir.absolutePath/test/resources/solr"
        solrPort = 8984
        solrStopPort = 8078 //default: 8079
      }
      break
    default:
      break
  }

}