package gettingstarted;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.content.commons.search.IndexService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileContentController {

	@Autowired 
	private FileRepository filesRepo;
	
	@Autowired 
	private FileContentStore contentStore;
	
	@Autowired 
	private ContentIndexServiceImpl indexServiceImpl;
	
	
	@RequestMapping(value="/files/{fileId}", method = RequestMethod.PUT)
	public ResponseEntity<?> setContent(@PathVariable("fileId") Long id, @RequestParam("file") MultipartFile file) 
			throws IOException {

		
		System.out.println("setContent method started " + id + " --- " + filesRepo.findAll().toString());
		
		Optional<Document> f = filesRepo.findById(id);
		System.out.println("File: " + f.toString());
		
		if (f.isPresent()) {
			
			f.get().setMimeType(file.getContentType());
			
			//Add createdBy field value
			f.get().setCreatedBy("paragbhusan");
			
			System.out.println("Storing file conttent ......... " + f.get().toString());
			
			//Storing Document in local file system
			contentStore.setContent(f.get(), file.getInputStream());
			
			//Creating Index on Elasticsearch engine
			indexServiceImpl.index(f.get(), file.getInputStream());
			
			System.out.println("Saving data to H2 ......... " + f.toString());
			
			// save updated content-related info
			filesRepo.save(f.get());
			System.out.println("Saved in H2 DB");

			return new ResponseEntity<Object>(HttpStatus.OK);
		}
		return null;
	}

	
	
	@RequestMapping(value="/files/{fileId}", method = RequestMethod.GET)
	public ResponseEntity<?> getContent(@PathVariable("fileId") Long id) {

		System.out.println("getContent method started with id: " + id);
		Optional<Document> f = filesRepo.findById(id);
		
		if (f.isPresent()) {
			InputStreamResource inputStreamResource = new InputStreamResource(contentStore.getContent(f.get()));
			HttpHeaders headers = new HttpHeaders();
			headers.setContentLength(f.get().getContentLength());
			headers.set("Content-Type", f.get().getMimeType());
			return new ResponseEntity<Object>(inputStreamResource, headers, HttpStatus.OK);
		}
		return null;
	}
	
	
	
	@RequestMapping(value="/files/{fileId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> unsetContent(@PathVariable("fileId") Long id) {
		
		System.out.println("unsetContent started");
		Optional<Document> f = filesRepo.findById(id);
		
		if (f.isPresent()) {
			
			//Delete document from Local file system
			contentStore.unsetContent(f.get());
			
			//Delete document from Elasticsearch Index
			indexServiceImpl.unindex(f.get());
			
			//delete content related info from DB
			filesRepo.delete(f.get());
			
			return new ResponseEntity<Object>(HttpStatus.OK);
		}
		return null;
	}
	
	
	@RequestMapping(value = "/files/search/{queryString}", method = RequestMethod.GET)
	public ResponseEntity<?> searchContent(@PathVariable("queryString") String queryString) {
		
		System.out.println("searchContent started with string " + queryString);
		Iterable<String> contentIdList = contentStore.search(queryString);
		System.out.println("contentIdList: " + contentIdList);
		StreamSupport.stream(contentIdList.spliterator(), false).count();
		
		if(StreamSupport.stream(contentIdList.spliterator(), false).count() >= 1) {
			
			List<String> fileNameList = new ArrayList<String>();
			for(String contentId : contentIdList) {
				System.out.println("Content Id: " + contentId);
				
				Optional<Document> document = filesRepo.findByContentId(contentId);
				if (document.isPresent()) {
					
					fileNameList.add(document.get().getName());
					
	//				InputStreamResource inputStreamResource = new InputStreamResource(contentStore.getContent(document.get()));
	//				HttpHeaders headers = new HttpHeaders();
	//				headers.setContentLength(document.get().getContentLength());
	//				headers.set("Content-Type", document.get().getMimeType());
	//				return new ResponseEntity<Object>(inputStreamResource, headers, HttpStatus.OK);
					
				}
				
			}
			
			return new ResponseEntity<Object>(fileNameList, HttpStatus.OK);
		}
		return null;
	}
	
}