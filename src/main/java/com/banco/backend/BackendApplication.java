package com.banco.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @Bean
    public MongoClient mongoClient() {
		return MongoClients.create("mongodb+srv://zs25023767_db_user:TMi48D9dwZvgrsVg@bancocluster.8xkurgn.mongodb.net/banco%3B?retryWrites=true&w=majority");
    }

    @Bean
    public MongoTemplate mongoTemplate() {
		return new MongoTemplate(mongoClient(), "banco;");
    }
}