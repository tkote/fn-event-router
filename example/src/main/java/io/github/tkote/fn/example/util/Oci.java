package io.github.tkote.fn.example.util;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;

import org.apache.commons.codec.binary.Base64;

public class Oci {
    private final static Logger logger = Logger.getLogger(Oci.class.getName());

    protected BasicAuthenticationDetailsProvider provider;

    public Oci(){
        try{
            provider = getOCIProvider();
        }catch(Exception e){
            throw new RuntimeException("Coundn't instanciate Oci - " + e.getMessage(), e);
        }
    }

    protected BasicAuthenticationDetailsProvider getOCIProvider(){
        String version = System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION");
        BasicAuthenticationDetailsProvider provider = null;
        if(Objects.nonNull(version)) {
            provider = ResourcePrincipalAuthenticationDetailsProvider.builder().build();
        }else{
            try{
                provider = new ConfigFileAuthenticationDetailsProvider("~/.oci/config", "DEFAULT");
            }catch (IOException e) {
                throw new RuntimeException("Couldn't create OCI Provider - " + e.getMessage(), e);
            }
        }
        return provider;
    }

    public DatabaseClient createDatabaseClient(){
        return new DatabaseClient(provider);
    }

    public String getSecret(String region, String secretOcid){
        logger.fine("Retrieving secret: " + secretOcid);
        try(SecretsClient secretsClient = new SecretsClient(provider)){
            secretsClient.setRegion(region);
            GetSecretBundleRequest getSecretBundleRequest = GetSecretBundleRequest
                .builder()
                .secretId(secretOcid)
                .stage(GetSecretBundleRequest.Stage.Current)
                .build();
            GetSecretBundleResponse getSecretBundleResponse = secretsClient.getSecretBundle(getSecretBundleRequest);
            Base64SecretBundleContentDetails base64SecretBundleContentDetails =
            (Base64SecretBundleContentDetails) getSecretBundleResponse.
                    getSecretBundle().getSecretBundleContent();
            byte[] secretValueDecoded = Base64.decodeBase64(base64SecretBundleContentDetails.getContent());
            return new String(secretValueDecoded);
        }catch(Exception e){
            throw new RuntimeException("Couldn't get content from secret - " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getSecretAsMap(String region, String secretOcid) throws JsonParseException, JsonMappingException, IOException{
        String secret = getSecret(region, secretOcid);
        ObjectMapper mapper = new ObjectMapper();
        return (Map<String, String>) mapper.readValue(secret, Map.class);
    }

}
