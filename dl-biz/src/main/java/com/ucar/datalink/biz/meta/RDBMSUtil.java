package com.ucar.datalink.biz.meta;

import com.google.common.collect.Lists;
import com.ucar.datalink.biz.dal.MediaDAO;
import com.ucar.datalink.biz.utils.DataLinkFactory;
import com.ucar.datalink.biz.utils.DataSourceFactory;
import com.ucar.datalink.biz.utils.ddl.DdlUtils;
import com.ucar.datalink.common.errors.DatalinkException;
import com.ucar.datalink.domain.media.*;
import com.ucar.datalink.domain.media.parameter.MediaSrcParameter;
import com.ucar.datalink.domain.media.parameter.rdb.RdbMediaSrcParameter;
import com.ucar.datalink.domain.meta.ColumnMeta;
import com.ucar.datalink.domain.meta.MediaMeta;
import org.apache.commons.lang.StringUtils;
import org.apache.ddlutils.model.Column;
import org.apache.ddlutils.model.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 关系型数据库获取元数据的工具类
 */
public class RDBMSUtil {

    private static final Logger logger = LoggerFactory.getLogger(RDBMSUtil.class);
    private static final String MYSQL_URL = "jdbc:mysql://{0}:{1}/{2}";
    private static final String SQLSERVER_URL = "jdbc:sqlserver://{0}:{1};DatabaseName={2}";
    private static final String POSTGRESQL_URL = "jdbc:postgresql://{0}:{1}/{2}";
    private static final String ORACLE_URL = "jdbc:oracle:thin:@{0}:{1}:{2}";
    private static final String HAHA_URL = "jdbc:sap://{0}:{1}/{2}";

    /**
     * 获取表结构元信息
     *
     * @param info
     * @return
     * @throws Exception
     */
    public static List<MediaMeta> getTables(MediaSourceInfo info) {
        check(info.getParameterObj());
        RdbMediaSrcParameter parameter = info.getParameterObj();
        DataSource ds = DataSourceFactory.getDataSource(info);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        MediaSourceType mediaSourceType = info.getType();

        List<MediaMeta> metas = new ArrayList<>();
        //oracle
        if (mediaSourceType == MediaSourceType.ORACLE) {
            DatabaseMetaData metaData = null;
            Connection connection = null;
            ResultSet rs = null;
            try {
                String nameSpace = info.getParameterObj().getNamespace();
                String name = info.getName();
                //String desc = info.getDesc();
                String dbName = name.replace(nameSpace + "#", "");
                dbName = dbName.toUpperCase();
                connection = jdbcTemplate.getDataSource().getConnection();
                metaData = connection.getMetaData();
                rs = metaData.getTables(null, dbName, null, new String[]{"TABLE", "VIEW"});
                while (rs.next()) {
                    MediaMeta mm = new MediaMeta();
                    mm.setDbType(info.getType());
                    mm.setNameSpace(parameter.getNamespace());
                    mm.setName(rs.getString("TABLE_NAME"));
                    metas.add(mm);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new DatalinkException("get media meta data failed.", e);
            } finally {
                JdbcUtils.closeResultSet(rs);
                closeConnection(connection);
            }
        } else if (mediaSourceType == MediaSourceType.HANA) {
            DatabaseMetaData metaData = null;
            Connection connection = null;
            ResultSet rs = null;
            try {
                String nameSpace = info.getParameterObj().getNamespace();
                //connection = jdbcTemplate.getDataSource().getConnection();
                RdbMediaSrcParameter rdbMediaSrcParameter = info.getParameterObj();
                String userName = rdbMediaSrcParameter.getReadConfig().getUsername();
                String password = rdbMediaSrcParameter.getReadConfig().getDecryptPassword();
                String host = rdbMediaSrcParameter.getReadConfig().getEtlHost();
                String port = rdbMediaSrcParameter.getPort() + "";
                String url = MessageFormat.format(HAHA_URL, host, port, nameSpace);
                connection = DriverManager.getConnection(url, userName, password);

                metaData = connection.getMetaData();
                rs = metaData.getTables(null, nameSpace, null, new String[]{"TABLE"});
                while (rs.next()) {
                    MediaMeta mm = new MediaMeta();
                    mm.setDbType(info.getType());
                    mm.setNameSpace(parameter.getNamespace());
                    mm.setName(rs.getString("TABLE_NAME"));
                    metas.add(mm);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new DatalinkException("get media meta data failed.", e);
            } finally {
                JdbcUtils.closeResultSet(rs);
                closeConnection(connection);
            }
        }
        //sql server和PostreSql
        else if (mediaSourceType == MediaSourceType.SQLSERVER || mediaSourceType == MediaSourceType.POSTGRESQL) {
            DatabaseMetaData metaData = null;
            Connection connection = null;
            ResultSet rs = null;
            try {
                connection = jdbcTemplate.getDataSource().getConnection();
                metaData = connection.getMetaData();
                rs = metaData.getTables(null, null, null, new String[]{"TABLE"});
                while (rs.next()) {
                    MediaMeta mm = new MediaMeta();
                    mm.setDbType(info.getType());
                    mm.setNameSpace(parameter.getNamespace());
                    mm.setName(rs.getString("TABLE_NAME"));
                    metas.add(mm);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new DatalinkException("get media meta data failed.", e);
            } finally {
                JdbcUtils.closeResultSet(rs);
                closeConnection(connection);
            }
        }
        //mysql,sddl
        else {
            try {
                List<Table> tables = DdlUtils.findTables(jdbcTemplate, parameter.getNamespace(), parameter.getNamespace(), "%", null, null);
                for (Table t : tables) {
                    String tableName = t.getName();
                    String type = t.getType();
                    Column[] columns = t.getColumns();
                    List<ColumnMeta> cols = new ArrayList<>();
                    for (Column c : columns) {
                        ColumnMeta cm = new ColumnMeta();
                        cm.setName(c.getName());
                        cm.setType(c.getType());
                        cm.setLength(c.getSizeAsInt());
                        cm.setDecimalDigits(c.getPrecisionRadix());
                        cols.add(cm);
                    }
                    MediaMeta tm = new MediaMeta();
                    tm.setName(tableName);
                    tm.setNameSpace(info.getParameterObj().getNamespace());
                    tm.setColumn(cols);
                    metas.add(tm);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new DatalinkException("get media meta data failed.", e);
            }
        }
        return metas;
    }

    /**
     * 获取数据源下的表名
     *
     * @param info
     * @return
     * @throws Exception
     */
    public static List<String> getTableName(MediaSourceInfo info) {
        List<MediaMeta> metas = getTables(info);
        List<String> tableNameList = new ArrayList<>();
        for (MediaMeta mediaMeta : metas) {
            tableNameList.add(mediaMeta.getName());
        }
        return tableNameList;
    }

    /**
     * 获取一张表的列元信息
     *
     * @param tableName
     * @return
     * @throws Exception
     */
    public static List<ColumnMeta> getColumns(MediaSourceInfo info, String tableName) {
        check(info.getParameterObj());
        DataSource ds = DataSourceFactory.getDataSource(info);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        MediaSourceType mediaSourceType = info.getType();

        List<ColumnMeta> list = new ArrayList<>();
        //sql server和PostreSql
        if (mediaSourceType == MediaSourceType.SQLSERVER || mediaSourceType == MediaSourceType.POSTGRESQL) {
            ResultSet rs = null;
            ResultSet primarySet = null;
            try {
                DatabaseMetaData metaData = jdbcTemplate.getDataSource().getConnection().getMetaData();

                primarySet = metaData.getPrimaryKeys(null, null, tableName);
                String columnName_pk = "";
                while (primarySet.next()) {
                    columnName_pk = primarySet.getString("COLUMN_NAME");
                }
                rs = metaData.getColumns(null, null, tableName, null);
                while (rs.next()) {
                    ColumnMeta cm = new ColumnMeta();
                    String columnName = rs.getString("COLUMN_NAME");
                    cm.setName(columnName);
                    cm.setType(rs.getString("TYPE_NAME"));
                    cm.setLength(parseToInt(rs.getString("COLUMN_SIZE")));
                    cm.setDecimalDigits(parseToInt(rs.getString("DECIMAL_DIGITS")));
                    cm.setColumnDesc(rs.getString("REMARKS"));
                    try {
                        if ("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))) {
                            cm.setIsAutoIncrement(true);
                        } else {
                            cm.setIsAutoIncrement(false);
                        }
                    } catch (Exception e) {
                        cm.setIsAutoIncrement(false);
                    }
                    if (isPrimaryKey(columnName_pk, columnName)) {
                        cm.setIsPrimaryKey(true);
                    }
                    list.add(cm);
                }
            } catch (Exception e) {
                logger.error("get columnInfo is error", e);
                throw new DatalinkException("get column meta data failed.", e);
            } finally {
                JdbcUtils.closeResultSet(rs);
                JdbcUtils.closeResultSet(primarySet);
            }
        } else if (mediaSourceType == MediaSourceType.ORACLE) {
            ResultSet rs = null;
            ResultSet primarySet = null;
            try {
                DatabaseMetaData metaData = jdbcTemplate.getDataSource().getConnection().getMetaData();

                primarySet = metaData.getPrimaryKeys(null, null, tableName);
                String columnName_pk = "";
                while (primarySet.next()) {
                    columnName_pk = primarySet.getString("COLUMN_NAME");
                }
                rs = metaData.getColumns(null, null, tableName, null);
                Set<String> nameSet = new HashSet<>();
                while (rs.next()) {
                    ColumnMeta cm = new ColumnMeta();
                    String columnName = rs.getString("COLUMN_NAME");
                    if (nameSet.contains(columnName)) {
                        continue;
                    }
                    nameSet.add(columnName);
                    cm.setName(columnName);
                    cm.setType(rs.getString("TYPE_NAME"));
                    cm.setLength(parseToInt(rs.getString("COLUMN_SIZE")));
                    cm.setDecimalDigits(parseToInt(rs.getString("DECIMAL_DIGITS")));
                    cm.setColumnDesc(rs.getString("REMARKS"));
                    try {
                        if ("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))) {
                            cm.setIsAutoIncrement(true);
                        } else {
                            cm.setIsAutoIncrement(false);
                        }
                    } catch (Exception e) {
                        cm.setIsAutoIncrement(false);
                    }
                    if (isPrimaryKey(columnName_pk, columnName)) {
                        cm.setIsPrimaryKey(true);
                    }
                    list.add(cm);
                }
            } catch (Exception e) {
                logger.error("get columnInfo is error", e);
                throw new DatalinkException("get column meta data failed.", e);
            } finally {
                JdbcUtils.closeResultSet(rs);
                JdbcUtils.closeResultSet(primarySet);
            }
        } else if (mediaSourceType == MediaSourceType.HANA) {
            ResultSet rs = null;
            ResultSet primarySet = null;
            try {
                String nameSpace = info.getParameterObj().getNamespace();
                RdbMediaSrcParameter rdbMediaSrcParameter = info.getParameterObj();
                String userName = rdbMediaSrcParameter.getReadConfig().getUsername();
                String password = rdbMediaSrcParameter.getReadConfig().getDecryptPassword();
                String host = rdbMediaSrcParameter.getReadConfig().getEtlHost();
                String port = rdbMediaSrcParameter.getPort() + "";
                String url = MessageFormat.format(HAHA_URL, host, port, nameSpace);
                DatabaseMetaData metaData = DriverManager.getConnection(url, userName, password).getMetaData();

                primarySet = metaData.getPrimaryKeys(null, null, tableName);
                String columnName_pk = "";
                while (primarySet.next()) {
                    columnName_pk = primarySet.getString("COLUMN_NAME");
                }
                rs = metaData.getColumns(null, null, tableName, null);
                while (rs.next()) {
                    ColumnMeta cm = new ColumnMeta();
                    String columnName = rs.getString("COLUMN_NAME");
                    cm.setName(columnName);
                    cm.setType(rs.getString("TYPE_NAME"));
                    cm.setLength(parseToInt(rs.getString("COLUMN_SIZE")));
                    cm.setDecimalDigits(parseToInt(rs.getString("DECIMAL_DIGITS")));
                    cm.setColumnDesc(rs.getString("REMARKS"));
                    try {
                        if ("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))) {
                            cm.setIsAutoIncrement(true);
                        } else {
                            cm.setIsAutoIncrement(false);
                        }
                    } catch (Exception e) {
                        cm.setIsAutoIncrement(false);
                    }
                    if (isPrimaryKey(columnName_pk, columnName)) {
                        cm.setIsPrimaryKey(true);
                    }
                    list.add(cm);
                }
            } catch (Exception e) {
                logger.error("get columnInfo is error", e);
                throw new DatalinkException("get column meta data failed.", e);
            } finally {
                JdbcUtils.closeResultSet(rs);
                JdbcUtils.closeResultSet(primarySet);
            }
        } else {
            try {
                RdbMediaSrcParameter parameter = info.getParameterObj();
                Table table = DdlUtils.findTable(jdbcTemplate, parameter.getNamespace(), parameter.getNamespace(), tableName);
                if (table == null) {
                    return list;
                }
                Column[] columns = table.getColumns();
                for (Column c : columns) {
                    ColumnMeta cm = new ColumnMeta();
                    if (c.isPrimaryKey()) {
                        cm.setIsPrimaryKey(true);
                    }
                    cm.setName(c.getName());
                    cm.setType(c.getType());
                    cm.setLength(c.getSizeAsInt());
                    cm.setDecimalDigits(c.getScale());
                    cm.setColumnDesc(c.getDescription());
                    cm.setIsAutoIncrement(c.isAutoIncrement());
                    list.add(cm);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new DatalinkException("get column meta data failed.", e);
            }
        }

        return list;
    }

    /**
     * 获取一张表的列名
     *
     * @param info
     * @param tableName
     * @return
     */
    public static List<String> getColumnName(MediaSourceInfo info, String tableName) {
        List<ColumnMeta> metas = getColumns(info, tableName);
        List<String> columnNameList = new ArrayList<String>();
        for (ColumnMeta columnMeta : metas) {
            columnNameList.add(columnMeta.getName());
        }
        return columnNameList;
    }

    /**
     * 检查数据库是否可访问
     *
     * @param type
     * @param ip
     * @param port
     * @param schema
     * @param username
     * @param password
     * @return
     * @throws Exception
     */
    public static Boolean checkRdbConnection(String type, String ip, int port, String schema, String username, String password) throws Exception {
        String url = getUrl(type, ip, port, schema);
        checkConnection(url, username, password);
        return true;
    }

    private static String getUrl(String type, String ip, int port, String schema) {
        String url = "";
        if (MediaSourceType.MYSQL.name().equals(type)) {
            url = MessageFormat.format(MYSQL_URL, ip, port + "", schema);
        } else if (MediaSourceType.SQLSERVER.name().equals(type)) {
            url = MessageFormat.format(SQLSERVER_URL, ip, port + "", schema);
        } else if (MediaSourceType.POSTGRESQL.name().equals(type)) {
            url = MessageFormat.format(POSTGRESQL_URL, ip, port + "", schema);
        } else if (MediaSourceType.ORACLE.name().equals(type)) {
            url = MessageFormat.format(ORACLE_URL, ip, port + "", schema);
        }
        return url;
    }

    /**
     * 检查数据
     *
     * @param mappingId
     * @param startId
     * @param endId
     * @return
     */
    public static DataCheckResult checkData(Long mappingId, Long startId, Long endId) {
        if (mappingId == null) {
            throw new IllegalArgumentException("mappingId can not be null.");
        }
        DataCheckResult checkResult = new DataCheckResult();
        if (startId == null) {
            startId = 0L;
        }
        if (endId == null) {
            endId = Long.MAX_VALUE;
        }
        // common variables

        MediaMappingInfo mappingInfo = DataLinkFactory.getObject(MediaDAO.class).findMediaMappingsById(mappingId);
        MediaInfo sourceMedia = mappingInfo.getSourceMedia();
        MediaSourceInfo sourceMediaSource = sourceMedia.getMediaSource();
        MediaSourceInfo targetMediaSource = mappingInfo.getTargetMediaSource();

        //sourceMediaSource query
        try {
            DataSource dataSource = DataSourceFactory.getDataSource(sourceMediaSource);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String namespace = sourceMediaSource.getParameterObj().getNamespace();
            String tableName = sourceMedia.getName();
            Table table = DdlUtils.findTable(jdbcTemplate, namespace, namespace, tableName);
            if (table != null) {
                Column[] primaryCols = table.getPrimaryKeyColumns();
                if (primaryCols.length > 1) {
                    throw new RuntimeException("不支持联合主键 : [" + primaryCols + "]");
                } else if (primaryCols.length == 1) {
                    String sql = MessageFormat.format("select min({0}) as minId,max({0}) as maxId,count({0}) as count from {1} where {0}>={2} and {0}<={3};", primaryCols[0].getName(), tableName,
                            startId.toString(), endId.toString());
                    Map<String, Object> result = jdbcTemplate.queryForMap(sql);
                    checkResult.setFromMinId(Long.valueOf(result.get("minId").toString()));
                    checkResult.setFromMaxId(Long.valueOf(result.get("maxId").toString()));
                    checkResult.setFromDsCount(Long.valueOf(result.get("count").toString()));
                }
            } else {
                throw new RuntimeException("同步数据源[" + namespace + "]里没有找到表 : [" + tableName + "]");
            }
        } catch (Exception e) {
            throw new RuntimeException("JDBC执行错误", e);
        } finally {
            DataSourceFactory.invalidate(sourceMediaSource, () -> null);
        }
        //targetMediaSource query
        try {
            DataSource dataSource = DataSourceFactory.getDataSource(targetMediaSource);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String namespace = targetMediaSource.getParameterObj().getNamespace();
            String tableName = mappingInfo.getTargetMediaName();
            Table table = DdlUtils.findTable(jdbcTemplate, namespace, namespace, tableName);
            if (table != null) {
                Column[] primaryCols = table.getPrimaryKeyColumns();
                if (primaryCols.length > 1) {
                    throw new RuntimeException("不支持联合主键 : [" + primaryCols + "]");
                } else if (primaryCols.length == 1) {
                    String sql = MessageFormat.format("select min({0}) as minId,max({0}) as maxId,count({0}) as count from {1} where {0}>={2} and {0}<={3};", primaryCols[0].getName(), tableName,
                            startId.toString(), endId.toString());
                    Map<String, Object> result = jdbcTemplate.queryForMap(sql);
                    checkResult.setToMinId(Long.valueOf(result.get("minId").toString()));
                    checkResult.setToMaxId(Long.valueOf(result.get("maxId").toString()));
                    checkResult.setToDsCount(Long.valueOf(result.get("count").toString()));
                }
            } else {
                throw new RuntimeException("目标数据源[" + namespace + "]里没有找到表 : [" + tableName + "]");
            }
        } catch (Exception e) {
            throw new RuntimeException("JDBC执行错误", e);
        } finally {
            DataSourceFactory.invalidate(targetMediaSource, () -> null);
        }

        return checkResult;
    }

    /**
     * 检查数据
     *
     * @param mediaSourceInfo
     * @param sql
     * @return
     */
    public static List<Map<String, Object>> executeSql(MediaSourceInfo mediaSourceInfo, String sql) {

        try {
            DataSource dataSource = DataSourceFactory.getDataSource(mediaSourceInfo);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
            return list;
        } catch (Exception e) {
            throw new RuntimeException("JDBC执行错误", e);
        } finally {
            DataSourceFactory.invalidate(mediaSourceInfo, () -> null);
        }

    }

    /**
     * 检查当前的MediaSrcParameter类似是否是关系型数据库的类型，如果不是则抛异常
     *
     * @param msp
     */
    private static void check(MediaSrcParameter msp) {
        if (!(msp instanceof RdbMediaSrcParameter)) {
            throw new RuntimeException("当前的MediaSrcParameter类型错误 " + msp);
        }
    }

    private static boolean isPrimaryKey(String pk_name, String column) {
        if (StringUtils.isNotBlank(pk_name)) {
            if (pk_name.equals(column)) {
                return true;
            }
        }
        return false;
    }


    private static int parseToInt(String str) {
        if (str == null || "".equals(str)) {
            return 0;
        }
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void checkConnection(String url, String username, String password) throws Exception {

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(url, username, password);
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select 1");
        } finally {
            close(conn, stmt, rs);
        }
    }

    private static void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.error("", e);
            }
        }
    }

    private static void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.error("", e);
            }
        }
    }

    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error("", e);
            }
        }
    }

    private static void close(Connection conn, Statement stmt, ResultSet rs) {
        closeResultSet(rs);
        closeStatement(stmt);
        closeConnection(conn);
    }


    public static List<String> checkTargetTables(MediaSourceInfo realTargetMediaSourceInfo, String[] sourceTableName, Set<String> tableNameSet) throws Exception {
        List<String> returnList = Lists.newArrayList();
        List<MediaMeta> mediaMetaList = getTables(realTargetMediaSourceInfo);
        List<String> tableNameList = mediaMetaList.stream().map(m -> {
            return m.getName().toLowerCase();
        }).collect(Collectors.toList());
        Iterator<String> it = tableNameSet.iterator();
        while (it.hasNext()) {
            String targetTableName = it.next().toLowerCase();
            //源端是全表同步，则以下划线开头的表不校验
            if (sourceTableName != null && sourceTableName.length == 1
                    && sourceTableName[0].equals("(.*)") && targetTableName.startsWith("_")) {
                continue;
            }
            if (!tableNameList.contains(targetTableName)) {
                returnList.add(targetTableName);
            }
        }
        return returnList;
    }

    public static boolean hasPrimaryKey(MediaSourceInfo realMediaSourceInfo, String table) throws Exception {
        List<ColumnMeta> columnMetaList = getColumns(realMediaSourceInfo, table);
        for (ColumnMeta columnMeta : columnMetaList) {
            if (columnMeta.isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }
}
