package io.mixeway.rest.legacy.controller;

import io.mixeway.db.entity.CodeVuln;
import io.mixeway.domain.service.project.GetOrCreateProjectService;
import io.mixeway.plugins.audit.cisbenchmark.Service.CisDockerBenchmarkService;
import io.mixeway.plugins.audit.cisbenchmark.Service.CisK8sBenchmarkService;
import io.mixeway.plugins.audit.mvndependencycheck.model.SASTRequestVerify;
import io.mixeway.plugins.audit.mvndependencycheck.service.MvnDependencyCheckUploadService;
import io.mixeway.plugins.audit.vulners.model.Packets;
import io.mixeway.plugins.audit.vulners.service.VulnersService;
import io.mixeway.plugins.codescan.service.CodeScanClient;
import io.mixeway.plugins.codescan.service.CodeScanService;
import io.mixeway.plugins.infrastructurescan.model.NetworkScanRequestModel;
import io.mixeway.plugins.infrastructurescan.service.NetworkScanService;
import io.mixeway.plugins.utils.CodeAccessVerifier;
import io.mixeway.plugins.webappscan.model.WebAppScanRequestModel;
import io.mixeway.plugins.webappscan.service.WebAppScanService;
import io.mixeway.pojo.Status;
import org.codehaus.jettison.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.Semaphore;

@RestController
public class LegacyController {
    private final CisK8sBenchmarkService cisK8sBenchmarkService;
    private final CisDockerBenchmarkService cisDockerBenchmarkService;
    private final CodeAccessVerifier codeAccessVerifier;
    private final MvnDependencyCheckUploadService mvnDependencyCheckUploadService;
    private final VulnersService vulnersService;
    private final CodeScanService codeScanService;
    private final List<CodeScanClient> codeScanClients;
    private final NetworkScanService networkScanService;
    private final WebAppScanService webAppScanService;
    private final GetOrCreateProjectService projectService;
    private static Semaphore semaphore = new Semaphore(1);
    @Autowired
    LegacyController(CisK8sBenchmarkService cisK8sBenchmarkService, CisDockerBenchmarkService cisDockerBenchmarkService,
                     CodeAccessVerifier codeAccessVerifier, MvnDependencyCheckUploadService mvnDependencyCheckUploadService,
                     VulnersService vulnersService, CodeScanService codeScanService, List<CodeScanClient> codeScanClients,
                     NetworkScanService networkScanService, WebAppScanService webAppScanService, GetOrCreateProjectService projectService){
        this.cisK8sBenchmarkService = cisK8sBenchmarkService;
        this.codeAccessVerifier = codeAccessVerifier;
        this.mvnDependencyCheckUploadService = mvnDependencyCheckUploadService;
        this.cisDockerBenchmarkService = cisDockerBenchmarkService;
        this.vulnersService = vulnersService;
        this.codeScanService = codeScanService;
        this.codeScanClients = codeScanClients;
        this.networkScanService = networkScanService;
        this.projectService = projectService;
        this.webAppScanService = webAppScanService;
    }

    //@PreAuthorize("hasAuthority('ROLE_API')")
    @PostMapping(value = "/api/cis-k8s/{projectId}")
    public ResponseEntity<Status> getCisReport(@RequestParam("file") MultipartFile file, @PathVariable(value = "projectId") Long id) throws IOException {
        return cisK8sBenchmarkService.processK8sReport(file,id);
    }
    @PostMapping(value = "/api/cis-docker/{projectId}")
    public ResponseEntity<Status> getCisDocker(@RequestParam("file") MultipartFile file, @PathVariable(value = "projectId") Long id) {
        return cisDockerBenchmarkService.getCisDocker(file,id);
    }
    @CrossOrigin(origins="*")
    @PreAuthorize("hasAuthority('ROLE_API')")
    @PostMapping(value = "/api/mvndependencycheck/{projectId}/{codeGroup}/{codeProject}",produces = "application/json")
    public ResponseEntity<Status> mvnDependencyCheck(@PathVariable(value = "codeGroup") String codeGroup,
                                                     @PathVariable(value = "codeProject") String codeProject,
                                                     @PathVariable(value = "projectId") Long id,
                                                     @RequestParam("file") MultipartFile file) throws IOException {
        SASTRequestVerify sastRequestVerify = codeAccessVerifier.verifyPermissions(id,codeGroup,codeProject,true);
        if (sastRequestVerify.getValid()) {
            return mvnDependencyCheckUploadService.mvnDependencyCheck(codeGroup, codeProject, id, file);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    @Transactional
    @PreAuthorize("permitAll()")
    @RequestMapping(method = RequestMethod.POST, value = "/api/packetdiscovery")
    public ResponseEntity<Status> packetDiscovery(@RequestBody Packets packets){

        return vulnersService.savePacketDiscovery(packets);

    }
    @PreAuthorize("hasAuthority('ROLE_API')")
    @RequestMapping(value = "/api/sast/{projectId}/create/{groupName}/{projectName}", method = RequestMethod.PUT,produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Status> createScanForProject(@PathVariable(value = "projectId") Long id,
                                                       @PathVariable(value="groupName") String groupName,
                                                       @PathVariable(value="projectName") String projectName) throws IOException, CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, JSONException, ParseException {
        SASTRequestVerify sastRequestVerify = codeAccessVerifier.verifyPermissions(id,groupName,projectName,false);
        if (sastRequestVerify.getValid()){
            for(CodeScanClient codeScanClient : codeScanClients){
                if (codeScanClient.canProcessRequest(sastRequestVerify.getCg())){
                    if (codeScanClient.runScan(sastRequestVerify.getCg(),sastRequestVerify.getCp())){
                        return new ResponseEntity<>(new Status("OK"), HttpStatus.CREATED);
                    } else {
                        return new ResponseEntity<>(new Status("Queued"), HttpStatus.CREATED);
                    }
                }
            }
        } else {
            return new ResponseEntity<>(new Status("Scan for given resource is not yet configured."), HttpStatus.PRECONDITION_FAILED);
        }
        return new ResponseEntity<>(new Status("Something went wrong"), HttpStatus.PRECONDITION_FAILED);
    }
    @PreAuthorize("hasAuthority('ROLE_API')")
    @RequestMapping(value = "/api/sast/{projectId}/running/{groupName}/{projectName}/{jobId}", method = RequestMethod.PUT,produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Status> putInformationAboutJob(@PathVariable(value = "projectId") Long id,
                                                         @PathVariable(value="groupName") String groupName,
                                                         @PathVariable(value="projectName") String projectName,
                                                         @PathVariable(value="jobId") String jobId) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, JSONException, ParseException {
        SASTRequestVerify sastRequestVerify = codeAccessVerifier.verifyPermissions(id,groupName,projectName,false);
        if (sastRequestVerify.getValid()){
            for(CodeScanClient codeScanClient : codeScanClients){
                if (codeScanClient.canProcessRequest(sastRequestVerify.getCg())){
                    codeScanClient.putInformationAboutScanFromRemote(sastRequestVerify.getCp(), sastRequestVerify.getCg(), jobId);
                    return new ResponseEntity<>(new Status("OK"), HttpStatus.OK);
                }
            }
        } else {
            return new ResponseEntity<>(new Status("Scan for given resource is not yet configured."), HttpStatus.PRECONDITION_FAILED);
        }
        return new ResponseEntity<>(new Status("Something went wrong"), HttpStatus.PRECONDITION_FAILED);
    }

    @PreAuthorize("hasAuthority('ROLE_API')")
    @GetMapping("/api/sast/show/{projectId}/{groupName}/{projectNane}")
    public ResponseEntity<List<CodeVuln>> getResultsForProjectScan(@PathVariable(value = "projectId") Long id,
                                                                   @PathVariable(value="groupName") String groupName,
                                                                   @PathVariable(value="projectNane") String projectName)  {

        return codeScanService.getResultsForProject(id,groupName,projectName);
    }
    @PreAuthorize("hasAuthority('ROLE_API')")
    @RequestMapping(value = "/api/koordynator/network",method = RequestMethod.POST)
    public ResponseEntity<Status> createAndRunNetworkscan(@RequestBody NetworkScanRequestModel req) throws Exception {
        return networkScanService.createAndRunNetworkScan(req);
    }
    @PreAuthorize("hasAuthority('ROLE_API')")
    @RequestMapping(value = "/api/koordynator/network/check/{ciid}",method = RequestMethod.GET)
    public ResponseEntity<Status> checkNetworkScanTest(@PathVariable("ciid") String ciid) {
        return networkScanService.checkScanStatusForCiid(ciid);
    }
    @Transactional
    @PreAuthorize("hasAuthority('ROLE_API')")
    @PostMapping(value = "/api/webapp/{projectId}")
    public ResponseEntity<Status> getWebApp(@PathVariable(value = "projectId") Long id, @RequestBody WebAppScanRequestModel req) throws InterruptedException {
        semaphore.acquire();
        try {
            return webAppScanService.processScanWebAppRequest(id, req.getWebApp());
        } finally {
            semaphore.release();
        }
    }
    @Transactional
    @PreAuthorize("hasAuthority('ROLE_API')")
    @PostMapping(value = "/api/koordynator/webapp")
    public ResponseEntity<Status> createWebAppScanFromKoordynator(@RequestBody WebAppScanRequestModel req) {
        String ciid = req.getCiid().orElse("");
        String projectName = req.getProjectName().orElse("");
        return webAppScanService.processScanWebAppRequest(projectService.getProjectId(ciid, projectName), req.getWebApp());
    }


}
