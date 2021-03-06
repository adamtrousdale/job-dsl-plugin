package javaposse.jobdsl.plugin

import hudson.ExtensionList
import hudson.ExtensionListListener
import hudson.Plugin
import hudson.model.Descriptor
import jenkins.model.Jenkins
import net.sf.json.JSONObject
import org.kohsuke.stapler.StaplerRequest
import org.kohsuke.stapler.StaplerResponse

import javax.servlet.ServletException

class JobDslPlugin extends Plugin {
    private volatile CachedFile cachedApi
    private volatile CachedFile cachedUpdateCenter

    @Override
    void postInitialize() throws Exception {
        ExtensionList.lookup(Descriptor).addListener(new ExtensionListListener() {
            @Override
            void onChange() {
                cachedApi = null
            }
        })
    }

    @Override
    void doDynamic(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        String path = request.restOfPath
        if (path == '/api-viewer') {
            response.sendRedirect("${request.requestURI}${request.requestURI.endsWith('/') ? '' : '/'}index.html")
        } else if (path == '/api-viewer/build/data/update-center.jsonp') {
            serveCachedFile(response, request, generateUpdateCenter(), 'update-center.js')
        } else if (path == '/api-viewer/build/data/dsl.json') {
            serveCachedFile(response, request, generateApi(), 'dsl.json')
        } else {
            super.doDynamic(request, response)
        }
    }

    private CachedFile generateApi() {
        CachedFile api = cachedApi
        if (api == null) {
            api = new CachedFile(new EmbeddedApiDocGenerator().generateApi(), System.currentTimeMillis())
            cachedApi = api
        }
        api
    }

    private CachedFile generateUpdateCenter() {
        long lastModified = Jenkins.instance.updateCenter.sites*.dataTimestamp.max()
        CachedFile updateCenter = cachedUpdateCenter
        if (updateCenter == null || lastModified > updateCenter.timestamp) {
            Map<String, Object> plugins = [:]
            Jenkins.instance.updateCenter.sites.each {
                plugins.putAll(it.JSONObject.getJSONObject('plugins'))
            }

            JSONObject data = new JSONObject()
            data['plugins'] = plugins

            updateCenter = new CachedFile("updateCenter.post(${data.toString()})", lastModified)
            cachedUpdateCenter = updateCenter
        }
        updateCenter
    }

    private static serveCachedFile(StaplerResponse response, StaplerRequest request, CachedFile file, String fileName) {
        response.serveFile(
                request,
                new ByteArrayInputStream(file.data),
                file.timestamp,
                0,
                file.data.length as long,
                fileName
        )
    }

    private static class CachedFile {
        final byte[] data
        final long timestamp

        CachedFile(String data, long timestamp) {
            this.data = data.getBytes('UTF-8')
            this.timestamp = timestamp
        }
    }
}
