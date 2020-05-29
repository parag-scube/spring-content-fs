package gettingstarted;


import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class SpringContentApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringContentApplication.class, args);
		
	}
	
	@Configuration
	//@EnableElasticsearchFulltextIndexing
	//@EnableFilesystemStores
	public static class ApplicationConfig {
		
		@Bean
		public RestHighLevelClient client() {
			RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));
			return restHighLevelClient;
	    }
	}
	
}
