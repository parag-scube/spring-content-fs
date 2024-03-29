package gettingstarted;

import static java.lang.String.format;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.ingest.GetPipelineRequest;
import org.elasticsearch.action.ingest.GetPipelineResponse;
import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.content.commons.annotations.ContentId;
//import org.springframework.content.commons.annotations.MimeType;
//import org.springframework.content.commons.renditions.RenditionService;
import org.springframework.content.commons.repository.StoreAccessException;
import org.springframework.content.commons.search.IndexService;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import internal.org.springframework.content.elasticsearch.IndexManager;


@Service
public class ContentIndexServiceImpl implements IndexService<Document> {
	
	private static final Log LOGGER = LogFactory.getLog(ContentIndexServiceImpl.class);
    private static final String SPRING_CONTENT_ATTACHMENT = "spring-content-attachment-pipeline";
    private static final int BUFFER_SIZE = 3 * 1024;

    private final RestHighLevelClient client;
    //private final RenditionService renditionService;
    private final IndexManager manager;

    private boolean pipelinedInitialized = false;
    
    
//    public DocumentIndexServiceImpl(RestHighLevelClient client, RenditionService renditionService, IndexManager manager) {
//
//        this.client = client;
//        this.renditionService = renditionService;
//        this.manager = manager;
//    }
    
    @Autowired
    public ContentIndexServiceImpl(RestHighLevelClient client, IndexManager manager) {
    	
    	        this.client = client;
//    	        this.renditionService = renditionService;
    	        this.manager = manager;
    }

    
	@Override
	public void index(Document document, InputStream content) {
		
		//Document f = (Document)entity;
		System.out.println("DocumentIndexServiceImpl index method started " + document.getName() + " -- Content: " + content.toString());
		
		
		if (!pipelinedInitialized) {
            try {
                ensureAttachmentPipeline();
            } catch (IOException ioe) {
                throw new StoreAccessException("Unable to initialize attachment pipeline", ioe);
            }
        }
		
		

        String id = BeanUtils.getFieldWithAnnotation(document, ContentId.class).toString();

//        if (renditionService != null) {
//            Object mimeType = BeanUtils.getFieldWithAnnotation(entity, MimeType.class);
//            if (mimeType != null) {
//                String strMimeType = mimeType.toString();
//                if (renditionService.canConvert(strMimeType, "text/plain")) {
//                	content = renditionService.convert(strMimeType, content, "text/plain");
//                }
//            }
//        }

        
        StringBuilder result = new StringBuilder();
        try {
            try (BufferedInputStream in = new BufferedInputStream(content, BUFFER_SIZE)) {
                Base64.Encoder encoder = Base64.getEncoder();
                byte[] chunk = new byte[BUFFER_SIZE];
                int len = 0;
                while ( (len = in.read(chunk)) == BUFFER_SIZE ) {
                    result.append( encoder.encodeToString(chunk) );
                }
                if ( len > 0 ) {
                    chunk = Arrays.copyOf(chunk,len);
                    result.append( encoder.encodeToString(chunk) );
                }
            }
        }
        catch (IOException e) {
            throw new StoreAccessException(format("Error base64 encoding stream for content %s", id), e);
        }
        
        IndexRequest req = new IndexRequest(manager.indexName(document.getClass()), document.getClass().getName(), id);
        req.setPipeline(SPRING_CONTENT_ATTACHMENT);

        //entity.setCreatedBy("paragbhusan");
//        String source = "{" +
//                "\"data\": \"" + result.toString() + "\"," +
//                "\"fileName\": \"" + entity.getName() + "\"," +
//                "\"summary\": \"" + entity.getSummary() + "\"," +
//                "\"createdBy\": \"" + entity.getCreatedBy() + "\"" +
//                "}";
      //req.source(source, XContentType.JSON);
        
        //Preparing metadata for search purpose
        Map<String, Object> jsonMapSource = new HashMap<String, Object>();
        jsonMapSource.put("data", result.toString());
        jsonMapSource.put("fileName", document.getName());
        jsonMapSource.put("summary", document.getSummary());
        jsonMapSource.put("createdBy", document.getCreatedBy());
        
        req.source(jsonMapSource, XContentType.JSON);

        try {
            IndexResponse res = client.index(req, RequestOptions.DEFAULT);
            LOGGER.info(format("Content '%s' indexed with result %s", id, res.getResult()));
        }
        catch (IOException e) {
            throw new StoreAccessException(format("Error indexing content %s", id), e);
        }
		
	}

	@Override
	public void unindex(Document document) {
		if (!pipelinedInitialized) {
            try {
                ensureAttachmentPipeline();
            } catch (IOException ioe) {
                throw new StoreAccessException("Unable to initialize attachment pipeline", ioe);
            }
        }

        Object id = BeanUtils.getFieldWithAnnotation(document, ContentId.class);
        if (id == null) {
            return;
        }

        DeleteRequest req = new DeleteRequest(manager.indexName(document.getClass()), document.getClass().getName(), id.toString());
        try {
            DeleteResponse res = client.delete(req, RequestOptions.DEFAULT);
            LOGGER.info(format("Indexed content '%s' deleted with result %s", id, res.getResult()));
        }
        catch (ElasticsearchStatusException ese) {
            if (ese.status() != RestStatus.NOT_FOUND) {
                // TODO: re-throw as StoreIndexException
            }
        }
        catch (IOException e) {
            throw new StoreAccessException(format("Error deleting indexed content %s", id), e);
        }
		
	}
	
	
	void ensureAttachmentPipeline() throws IOException {
        GetPipelineRequest getRequest = new GetPipelineRequest(SPRING_CONTENT_ATTACHMENT);
        GetPipelineResponse res = client.ingest().getPipeline(getRequest, RequestOptions.DEFAULT);
        if (!res.isFound()) {
            String source = "{\"description\":\"Extract attachment information encoded in Base64 with UTF-8 charset\"," +
                    "\"processors\":[{\"attachment\":{\"field\":\"data\"}}]}";
            PutPipelineRequest put = new PutPipelineRequest(SPRING_CONTENT_ATTACHMENT,
                    new BytesArray(source.getBytes(StandardCharsets.UTF_8)),
                    XContentType.JSON);
            AcknowledgedResponse wpr = client.ingest().putPipeline(put, RequestOptions.DEFAULT);
            Assert.isTrue(wpr.isAcknowledged(), "Attachment pipeline not acknowledged by server");
        }
    }

	

}
