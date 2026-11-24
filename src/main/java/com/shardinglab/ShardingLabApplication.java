package com.shardinglab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

// Boot auto-configures exactly one DataSource from spring.datasource.*.
// We have one pool per shard, so we build them ourselves (Step 4) and opt out here.
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ShardingLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShardingLabApplication.class, args);
	}

}
