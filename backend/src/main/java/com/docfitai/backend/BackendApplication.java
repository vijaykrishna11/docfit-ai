package com.docfitai.backend;

import com.docfitai.backend.config.ProductionSafetyEnvironmentListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(BackendApplication.class);
		// Registered here (not component-scanned) so it runs on ApplicationEnvironmentPreparedEvent,
		// before the ApplicationContext exists -- see ProductionSafetyEnvironmentListener's Javadoc.
		application.addListeners(new ProductionSafetyEnvironmentListener());
		application.run(args);
	}

}
