package io.github.tkote.fn.example.util;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sql.DataSource;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnInit;
import com.fnproject.fn.api.RuntimeContext;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.model.GenerateAutonomousDatabaseWalletDetails;
import com.oracle.bmc.database.model.GenerateAutonomousDatabaseWalletDetails.GenerateType;
import com.oracle.bmc.database.requests.GenerateAutonomousDatabaseWalletRequest;
import com.oracle.bmc.database.responses.GenerateAutonomousDatabaseWalletResponse;
import com.oracle.bmc.http.internal.ResponseHelper;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

@FnBean
public class DatabaseUtil{
    private final static Logger logger = Logger.getLogger(DatabaseUtil.class.getName());

    private String region;
    private String adbId;
    private String walletDir;
    private String walletPw;
    private String username;
    private String password;
    private String jdbcUrl;

    private Map<String, String> config;
    private PoolDataSource dataSource;

    @FnInit
    public void onInit(RuntimeContext rctx){
        this.config = rctx.getConfiguration();
        this.region = config.get("OCI_REGION");
        this.adbId = config.get("ADB_ID");
        this.walletDir = config.get("ADB_WALLET_DIR");
        this.walletPw = config.get("ADB_WALLET_PW");
        this.jdbcUrl = config.get("JDBC_URL");
        this.username = config.get("JDBC_USERNAME");
        this.password = config.get("JDBC_PASSWORD");

        boolean enabled = Boolean.parseBoolean(config.getOrDefault("QUERY_ENABLED", "false"));
        if(enabled){
            try{
                if(Objects.nonNull(adbId)){
                    downloadWallet();
                }
                dataSource = createDataSource();
            }catch(Exception e){
                throw new RuntimeException("Failed to set up - " + e.getMessage(), e);
            }
        }
    }

    public Connection getConnection() throws SQLException{
        return dataSource.getConnection();
    }

    public DataSource getDataSource(){
        return dataSource;
    }

    private PoolDataSource createDataSource() throws SQLException{
        Objects.requireNonNull(jdbcUrl);

        logger.fine("jdbc url: " + jdbcUrl);
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        dataSource.setConnectionProperty("oracle.jdbc.ReadTimeout", "5000");
        dataSource.setConnectionProperty("oracle.jdbc.fanEnabled", "false");
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setURL(jdbcUrl);
        dataSource.setInitialPoolSize(1);
        dataSource.setMinPoolSize(1);
        dataSource.setMaxPoolSize(1);
        return dataSource;
    }

    public void downloadWallet() throws Exception{
        logger.fine("Wallet dir: " + walletDir);
        if(Objects.isNull(walletDir)){
            logger.fine("Wallet dir not set, skipped to download wallet files.");
            return;
        }else{
            if(Files.exists(Paths.get(walletDir))){
                logger.fine("Wallet dir exists, skipped to download wallet files.");
                return;
            }
        }
        Oci oci = new Oci();
        try(DatabaseClient client = oci.createDatabaseClient()){
            ResponseHelper.shouldAutoCloseResponseInputStream(false);
            client.setRegion(region);

            // download wallet
            GenerateAutonomousDatabaseWalletDetails details = GenerateAutonomousDatabaseWalletDetails.builder()
                .generateType(GenerateType.Single)
                .password(walletPw)
                .build();
            GenerateAutonomousDatabaseWalletRequest request = GenerateAutonomousDatabaseWalletRequest.builder()
                .generateAutonomousDatabaseWalletDetails(details)
                .autonomousDatabaseId(adbId)
                .build();
            GenerateAutonomousDatabaseWalletResponse response = client.generateAutonomousDatabaseWallet(request);

            try(ZipInputStream zin = new ZipInputStream(response.getInputStream())){
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-x---");
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                Files.createDirectories(Paths.get(walletDir), attr);

                ZipEntry zipentry = null;
                while(null != (zipentry = zin.getNextEntry())) {
                    logger.fine("Wallet file: " + zipentry.getName());
                    try(FileOutputStream fout = new FileOutputStream(walletDir + "/" + zipentry.getName());
                            BufferedOutputStream bout = new BufferedOutputStream(fout);
                            ){
                        byte[] data = new byte[1024];
                        int count = 0;
                        while((count = zin.read(data)) != -1){
                            bout.write(data,0,count);
                        }
                    }
                }
            }
        }
        logger.fine("Wallet files download complete.");
    }

}
