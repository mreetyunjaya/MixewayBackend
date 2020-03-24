package io.mixeway.plugins.webappscan.scheduler;


import java.util.*;

import io.mixeway.config.Constants;
import io.mixeway.db.entity.*;
import io.mixeway.db.entity.Scanner;
import io.mixeway.db.repository.*;
import io.mixeway.plugins.webappscan.WebAppScanClient;
import io.mixeway.plugins.webappscan.model.WebAppScanHelper;
import io.mixeway.plugins.webappscan.model.WebAppScanModel;
import io.mixeway.plugins.webappscan.service.WebAppScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import org.springframework.web.client.ResourceAccessException;


@Component
@Transactional
public class WebAppScheduler {
	private WebAppRepository waRepository;
	private ScannerRepository scannerRepository;
	private ScannerTypeRepository scannerTypeRepository;
	private WebAppVulnRepository vulnRepository;
	private WebAppScanService webAppService;
	private WebAppRepository webAppRepository;
	private ProjectRepository projectRepository;
	private List<WebAppScanClient> webAppScanClients;

    WebAppScheduler(WebAppRepository waRepository, ScannerRepository scannerRepository, WebAppScanService webAppService,
                    ScannerTypeRepository scannerTypeRepository, WebAppVulnRepository vulnRepository,
                    WebAppRepository webAppRepository, ProjectRepository projectRepository, List<WebAppScanClient> webAppScanClients){
		this.waRepository = waRepository;
		this.scannerRepository = scannerRepository;
		this.scannerTypeRepository = scannerTypeRepository;
		this.vulnRepository = vulnRepository;
		this.webAppRepository = webAppRepository;
		this.projectRepository = projectRepository;
		this.webAppScanClients = webAppScanClients;
		this.webAppService = webAppService;
	}
	private static final Logger log = LoggerFactory.getLogger(WebAppScheduler.class);


	@Scheduled(fixedRate = 3000)
	public void checkAndDownload() throws Exception {
		List<WebApp> apps = waRepository.findByRunning(true);
		ScannerType scannerType = scannerTypeRepository.findByNameIgnoreCase(Constants.SCANNER_TYPE_ACUNETIX);
		Optional<io.mixeway.db.entity.Scanner> scanner = scannerRepository.findByScannerType(scannerType).stream().findFirst();
		if (scanner.isPresent() && scanner.get().getStatus()) {
			for (WebApp app : apps) {
				try {
					for (WebAppScanClient webAppScanClient : webAppScanClients){
						if (webAppScanClient.canProcessRequest(scanner.get()) && webAppScanClient.isScanDone(scanner.get(), app)){
							List<WebAppVuln> tmpVulns =  new ArrayList<>();
							if (app.getVulns().size() > 0) {
								tmpVulns =  vulnRepository.findByWebApp(app);
								vulnRepository.deleteByWebApp(app);
								app = waRepository.getOne(app.getId());
							}
							webAppScanClient.loadVulnerabilities(scanner.get(), app, null, tmpVulns);
						}
					}
				} catch (HttpClientErrorException e) {
					if (e.getRawStatusCode() == 404) {
						deactivateWebApp(app);
						log.warn("WebApp deleted manualy from acunetix - {} {}", e.getRawStatusCode(), app.getUrl());
					} else
						log.warn("HttpClientException with code {} for webapp {}", e.getRawStatusCode(), app.getUrl());
				} catch (ResourceAccessException rae) {
					log.error("Scanner {} is not avaliable", scanner.get().getApiUrl());
				}
			}
		}
	}
	private void deactivateWebApp(WebApp app) {
		app.setRunning(false);
		webAppRepository.save(app);

	}
	//Every 5 min
	@Scheduled(fixedRate = 30000)
	public void runScanFromQueue() throws Exception {
		Long count = webAppRepository.getCountOfRunningScans(true);
		Optional<Scanner> scanner = scannerRepository.findByScannerType(scannerTypeRepository.findByNameIgnoreCase(Constants.SCANNER_TYPE_ACUNETIX)).stream().findFirst();
		int limit;
		List<WebApp> appsToScan = new ArrayList<>();
		if (count <= Constants.ACUNETIX_TARGET_LIMIT && scanner.isPresent() && scanner.get().getStatus()) {
			limit = (int) (Constants.ACUNETIX_TARGET_LIMIT - count);
			if (limit > 0) {
				appsToScan = webAppRepository.getXInQueue(true, limit);
			}
			for (WebApp webApp : appsToScan) {
				webApp.setInQueue(false);
				webAppRepository.save(webApp);
				for (WebAppScanClient webAppScanClient : webAppScanClients){
					if (webAppScanClient.canProcessRequest(scanner.get())){
						webAppScanClient.runScan(webApp,scanner.get());
					}
				}
				log.debug("Starget scan for {} taken from queue", webApp.getUrl());
			}
		}

	}

	@Scheduled(cron="#{@getWebAppCronExpresion}" )
	public void startAutomaticWebAppScans(){
		Optional<Scanner> scanner = scannerRepository.findByScannerType(scannerTypeRepository.findByNameIgnoreCase(Constants.SCANNER_TYPE_ACUNETIX)).stream().findFirst();
		//List<WebApp> webApps = webAppRepository.findByAutoStart(true);
		if (scanner.isPresent() && scanner.get().getStatus()) {
			List<Project> projects = projectRepository.findByAutoWebAppScan(true);
			for (Project p : projects) {
				for (WebApp webApp : p.getWebapps()) {
					webApp.setInQueue(true);
					webAppRepository.save(webApp);
				}
			}
		}
	}

	private List<WebAppScanModel> createServiceDiscoveryFromWebApp(WebApp webApp){
		List<WebAppScanModel> serviceDiscoveries = new ArrayList<>();
		WebAppScanModel sd = new WebAppScanModel();
		sd.setUrl(webApp.getUrl());
		sd.setIsPublic(webApp.getPublicscan());
		serviceDiscoveries.add(sd);
		return serviceDiscoveries;
	}




}
