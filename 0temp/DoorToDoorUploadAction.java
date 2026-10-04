package com.dianping.beauty.technician.action.doorToDoor;

import com.alibaba.fastjson.JSON;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.dianping.beauty.ibot.dto.TextBody;
import com.dianping.beauty.ibot.enums.Pusher;
import com.dianping.beauty.ibot.service.common.CommonPushBaseService;
import com.dianping.beauty.technician.action.doorToDoor.dto.*;
import com.dianping.beauty.technician.action.doorToDoor.enums.*;
import com.dianping.beauty.technician.action.doorToDoor.exception.DoorToDoorException;
import com.dianping.beauty.technician.action.doorToDoor.facade.request.SwarmCallBackRequest;
import com.dianping.beauty.technician.action.doorToDoor.utils.DoorToDoorUtils;
import com.dianping.beauty.technician.utils.LionConfigUtil;
import com.dianping.cat.Cat;
import com.dianping.lion.Environment;
import com.dianping.lion.client.Lion;
import com.dianping.lion.common.util.JsonUtils;
import com.dianping.open.platform.mvc.dispatch.UriMapping;
import com.dianping.open.platform.mvc.interceptor.WebContext;
import com.dianping.squirrel.client.StoreKey;
import com.dianping.squirrel.client.impl.redis.RedisStoreClient;
import com.dianping.technician.common.api.domain.TechnicianResp;
import com.dianping.technician.common.tools.exeception.BizException;
import com.dianping.technician.common.tools.utils.ResponseMaker;
import com.dianping.technician.dto.SMSRequest;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.collect.Maps;
import com.meituan.image.client.ImageUploadClient;
import com.meituan.image.client.impl.ImageUploadClientImpl;
import com.meituan.image.client.pojo.ImageResult;
import com.sankuai.lifeevent.serviceprovider.common.dto.Response;
import com.sankuai.lifeevent.serviceprovider.domain.api.SpTechnicianDomainService;
import com.sankuai.lifeevent.serviceprovider.domain.api.dto.QueryTechnicianAreaRelationReq;
import com.sankuai.lifeevent.serviceprovider.domain.api.dto.SpTechnicianDTO;
import com.sankuai.lifeevent.serviceprovider.domain.api.dto.SpTechnicianQueryRequest;
import com.sankuai.lifeevent.serviceprovider.domain.api.dto.TechnicianAreaRelationDTO;
import com.sankuai.lifeevent.serviceprovider.merchant.api.ServiceProviderQueryService;
import com.sankuai.lifeevent.serviceprovider.merchant.api.dto.ServiceProviderDTO;
import com.sankuai.technician.api.doorToDoor.tech.dto.*;
import com.sankuai.technician.api.doorToDoor.tech.enums.DoorToDoorMediaStatusEnum;
import com.sankuai.technician.api.doorToDoor.tech.enums.DoorToDoorTakeOrderRecordStatus;
import com.sankuai.technician.api.doorToDoor.tech.enums.ReportTypeEnum;
import com.sankuai.technician.api.doorToDoor.tech.enums.ServiceStageEnum;
import com.sankuai.technician.api.doorToDoor.tech.service.*;
import com.sankuai.technician.api.doorToDoor.tech.service.request.TechnicianSelfTestRequest;
import com.sankuai.technician.api.doorToDoor.tech.service.request.TechnicianUpdateMediaRequest;
import com.sankuai.technician.trade.api.doortodoor.dto.WorkOrderProblemChoiceDTO;
import com.sankuai.technician.trade.api.doortodoor.dto.WorkOrderProblemDTO;
import com.sankuai.technician.trade.api.doortodoor.enums.AssessAttrKeyEnum;
import com.sankuai.technician.trade.api.doortodoor.request.CreateOrderAndAttrRequest;
import com.sankuai.technician.trade.api.doortodoor.service.AssessLiabilityOrderProcessService;
import com.sankuai.trade.general.reserve.enums.ReserveOperatorEnum;
import com.sankuai.trade.general.reserve.request.CancelOrderRequest;
import com.sankuai.trade.general.reserve.response.ReserveResponse;
import com.sankuai.trade.general.reserve.service.ReserveOrderProcessService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.dianping.technician.service.TechnicianSendMessageService;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.sankuai.technician.api.doorToDoor.tech.enums.DoorToDoorCategoryEnum.*;

@Component
@UriMapping(value = "/doortodoor/upload", interceptors = {"loginInterceptor", "cross"})
@Slf4j
public class DoorToDoorUploadAction extends DoorToDoorBaseAction {

    @Autowired
    private DoorToDoorTakeOrderRecordService doorToDoorTakeOrderRecordService;
    @Autowired
    private DoorToDoorMediaService doorToDoorMediaService;
    @Resource
    private DoorToDoorTechServiceOrderQueryService doorToDoorTechServiceOrderQueryService;
    @Autowired
    private DoorToDoorTechnicianReportExceptionService doorToDoorTechnicianReportExceptionService;

    @Autowired
    private DoorToDoorTechnicianSelfTestService doorToDoorTechnicianSelfTestService;

    @Autowired
    private DoorToDoorTaskCenterService doorToDoorTaskCenterService;
    @Autowired
    private DoorToDoorServiceBcpService doorToDoorServiceBcpService;
    @Autowired
    private TechnicianSendMessageService technicianSendMessageService;

    @Autowired
    private AssessLiabilityOrderProcessService  assessLiabilityOrderProcessService;

    @Autowired
    private SpTechnicianDomainService spTechnicianDomainService;

    @Autowired
    private ServiceProviderQueryService serviceProviderQueryService;

    @Autowired
    private CommonPushBaseService commonPushBaseService;

    @Autowired
    private ReserveOrderProcessService reserveOrderProcessService;

    @Resource(name = "redisClientMedical")
    private RedisStoreClient redisStoreClient;

    private final static String CATEGORY = "doortodoor_upload_action";

    private static String CLIENT_ID = "technician-mobile-web.doortodoor.venus.client.id";
    private static String CLIENT_SECRET = "technician-mobile-web.doortodoor.venus.client.secret";
    private static String VENUS_BUCKET_NAME = "technicianfulfillment";
    private static final int NEW_VERSION = 3;
    // 自营保洁拍摄服务后视频的stage
    private static final int CLEAN_SELF_OWN_BRAND_UPLOAD_VIDEOS_AFTER_SERVICE = 5;

    private static ImageUploadClient client;

    // 添加重试器
    public static Retryer<String> retryer = RetryerBuilder.<String>newBuilder()
            .retryIfException() // 发生异常时重试
            .retryIfResult(Objects::isNull) // 结果为null时重试
            .withWaitStrategy(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS)) // 重试间隔100ms
            .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 最多重试3次
            .build();

    // 添加重试器
    public static Retryer<Date> dateRetryer = RetryerBuilder.<Date>newBuilder()
            .retryIfException() // 发生异常时重试
            .retryIfResult(Objects::isNull) // 结果为null时重试
            .withWaitStrategy(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS)) // 重试间隔100ms
            .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 最多重试3次
            .build();

    // 在类的顶部添加专门用于 set 操作的重试器
    public static Retryer<Void> setRetryer = RetryerBuilder.<Void>newBuilder()
            .retryIfException() // 发生异常时重试
            .withWaitStrategy(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS)) // 重试间隔100ms
            .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 最多重试3次
            .build();

    static {
        client = new ImageUploadClientImpl(VENUS_BUCKET_NAME, Lion.getString(Environment.getAppName(), CLIENT_ID), Lion.getString(Environment.getAppName(), CLIENT_SECRET));
        client.autoSetEnv();
    }

    private static AmazonS3 s3client;

    private static String ACCESS_KEY_LION = "technician-mobile-web.doortodoor.s3.access.key";
    private static String SECRET_KEY_LION = "technician-mobile-web.doortodoor.s3.secret.key";
    private static String ENDPOINT_LION = "technician-mobile-web.doortodoor.s3.endpoint";
    private static String S3BUCKET_NAME = "lifeevent-tech-video";
    private static final String MAC_NAME = "HmacSHA1";
    private static final String ENCODING = "UTF-8";
    private static final String UPLOAD_TEXT_LION = "technician-mobile-web.doortodoor.upload.text";
    private static final Integer EXAMPLE_TYPE = 1;
    private static final String INSPECTION_VERSION_KEY = "inspection.report.version";
    private static final String CATEGORY_VALUE = "status";

    //accessKey:用户的Access Key ID
    //secretKey:用户的Access Key Secret
    //hostname:MSS的endpoint服务地址
    static {
        AWSCredentials credentials = new BasicAWSCredentials(Lion.getString(Environment.getAppName(), ACCESS_KEY_LION), Lion.getString(Environment.getAppName(), SECRET_KEY_LION));
        ClientConfiguration configuration = new ClientConfiguration();
        // 默认协议为HTTPS。将这个值设置为Protocol.HTTP，则使用的是HTTP协议
        configuration.setProtocol(Protocol.HTTPS);
        //生成云存储api client
        s3client = new AmazonS3Client(credentials, configuration);
        //配置云存储服务地址
        //注意Endpoint只填写域名，不包含协议( http:// 或 https:// )
        s3client.setEndpoint(ENDPOINT_LION);
        //设置客户端生成的http请求hos格式，目前只支持path type的格式，不支持bucket域名的格式
        S3ClientOptions s3ClientOptions = new S3ClientOptions();
        s3ClientOptions.setPathStyleAccess(true);
        s3client.setS3ClientOptions(s3ClientOptions);
    }

    private byte[] hmacSHA1Encrypt(String encryptText, String encryptKey) throws Exception {
        Cat.logEvent("INVALID_METHOD", "com.dianping.beauty.technician.action.doorToDoor.DoorToDoorUploadAction.hmacSHA1Encrypt(java.lang.String,java.lang.String)");
        byte[] data = encryptKey.getBytes(ENCODING);
        //根据给定的字节数组构造一个密钥，第二参数指定一个密钥算法的名称
        SecretKey secretKey = new SecretKeySpec(data, MAC_NAME);
        //生成一个指定 Mac 算法 的 Mac 对象
        Mac mac = Mac.getInstance(MAC_NAME);
        //用给定密钥初始化 Mac 对象
        mac.init(secretKey);
        byte[] text = encryptText.getBytes(ENCODING);
        //完成 Mac 操作
        return mac.doFinal(text);
    }

    @UriMapping(value = "/presignedpic")
    public Object presignedPic() {
        Cat.logEvent("INVALID_METHOD", "com.dianping.beauty.technician.action.doorToDoor.DoorToDoorUploadAction.presignedPic()");
        try {
            int techId = getTechId();
            if (techId <= 0) {
                return TechnicianResp.fail("需要登录才能上传视频！");
            }
            String purpose = getStringParameter("purpose");
            if (!"idcard".equals(purpose)) {
                //服务单id
                String serviceOrderId = getStringParameter("serviceorderid");
                //校验服务单和手艺人是否匹配
                TechnicianResp<DoorToDoorTakeOrderRecordDTO> technicianResp = doorToDoorTakeOrderRecordService.queryTakeOrderByTechIdAndOrderId(techId, Long.parseLong(serviceOrderId));
                if (technicianResp.respFail() || technicianResp.getData() == null) {
                    return TechnicianResp.fail("手艺人与预约单状态不匹配");
                }
            }
            ImageResult result = client.getToken2();
            if (result.isSuccess()) {
                Map<String, Object> map = ResponseMaker.noRoot()
                        .merge("code", 200)
                        .merge("signature", result.getToken())
                        .merge("expireTime", result.getExpireTime())
                        .merge("bucket", VENUS_BUCKET_NAME)
                        .result();
                return TechnicianResp.success(map);
            } else {
                return TechnicianResp.fail("图片上传授权异常！");
            }
        } catch (Exception e) {
            log.error("获取图片上传授权异常", e);

        }
        return TechnicianResp.fail("图片上传授权异常！");
    }

    /**
     * 构造视频对象文件名，为保证唯一性，由userId、时间戳以及文件名拼接构成
     *
     * @param fileName
     * @param userId
     * @return
     */
    private String buildObjectName(String fileName, long userId) {
        Cat.logEvent("INVALID_METHOD", "com.dianping.beauty.technician.action.doorToDoor.DoorToDoorUploadAction.buildObjectName(java.lang.String,long)");
        StringBuilder objectNameBuilder = new StringBuilder("");
        objectNameBuilder.append(userId);
        objectNameBuilder.append("_");
        objectNameBuilder.append(System.currentTimeMillis());
        objectNameBuilder.append("_");
        objectNameBuilder.append(fileName);
        String objectName = objectNameBuilder.toString();
        return objectName;
    }

    @UriMapping(value = "/videosignature")
    public Object presignedVideo() {
        Cat.logEvent("INVALID_METHOD", "com.dianping.beauty.technician.action.doorToDoor.DoorToDoorUploadAction.presignedVideo()");
        int techId = getTechId();
        if (techId <= 0) {
            return TechnicianResp.fail("需要登录才能上传视频！");
        }
        //服务单id
        String serviceOrderId = getStringParameter("serviceorderid");
        //校验服务单和手艺人是否匹配
        TechnicianResp<DoorToDoorTakeOrderRecordDTO> technicianResp = doorToDoorTakeOrderRecordService.queryTakeOrderByTechIdAndOrderId(techId, Long.parseLong(serviceOrderId));
        if (technicianResp.respFail() || technicianResp.getData() == null) {
            return TechnicianResp.fail("手艺人与预约单状态不匹配");
        }

        String fileName = WebContext.getRequest().getParameter("filename");
        String objectName = buildObjectName(fileName, techId);
        String transObjectName = objectName.substring(0, objectName.lastIndexOf(".")) + ".mp4";
        String videoUrl = "https://" + ENDPOINT_LION + "/" + S3BUCKET_NAME + "/" + objectName;
        String transUrl = "https://" + ENDPOINT_LION + "/" + S3BUCKET_NAME + "/trans_" + transObjectName;
        List<String> list = getSignature();
        Map<String, Object> map = ResponseMaker.noRoot()
                .merge("AWSAccessKeyId", Lion.getString(Environment.getAppName(), ACCESS_KEY_LION))
                .merge("policy", list.get(1))
                .merge("signature", list.get(0))
                .merge("fileName", objectName)
                .merge("videoUrl", videoUrl)
                .merge("transUrl", transUrl)
                .result();
        return TechnicianResp.success(map);
    }

    @UriMapping(value = "/transcode/callback")
    public Object transcodeCallback() {
        Cat.logEvent("INVALID_METHOD", "com.dianping.beauty.technician.action.doorToDoor.DoorToDoorUploadAction.transcodeCallback()");
        try {
            SwarmCallBackRequest swarmCallBackRequest = getRequest(SwarmCallBackRequest.class);
            if (swarmCallBackRequest == null || swarmCallBackRequest.getStatus() != 0 || StringUtils.isBlank(swarmCallBackRequest.getId())) {
                log.error("调用转码服务失败，response={}", JSON.toJSONString(swarmCallBackRequest), new DoorToDoorException("调用转码服务失败"));
                return TechnicianResp.fail(500, "fail");
            }
            log.info("swarm transcodeCallback request {}", JSON.toJSONString(swarmCallBackRequest));
            String scope = swarmCallBackRequest.getScope();
            String saveAs = swarmCallBackRequest.getSaveAs();
            String videoName = scope.substring(scope.lastIndexOf("/") + 1);
            String transName = saveAs.substring(scope.lastIndexOf("/") + 1);
            String videoUrl = "https://" + Lion.getString(Environment.getAppName(), ENDPOINT_LION) + "/" + S3BUCKET_NAME + "/" + videoName;
            String transUrl = "https://" + Lion.getString(Environment.getAppName(), ENDPOINT_LION) + "/" + S3BUCKET_NAME + "/" + transName;
            TechnicianUpdateMediaRequest request = new TechnicianUpdateMediaRequest();
            request.setOriginUrl(videoUrl);
            request.setVideoTranscodeUrl(transUrl);
            request.setStatus(DoorToDoorMediaStatusEnum.TRANSCODING_SUCCESS.getCode());
            TechnicianResp res = doorToDoorMediaService.updateMedia(request);
            if (res == null || res.getCode() != 200) {
                log.error("视频转码失败", new DoorToDoorException("保存视频封面失败"));
            }
            DoorToDoorSubmitVideoAuditRequest auditRequest = new DoorToDoorSubmitVideoAuditRequest();
            auditRequest.setOriginUrl(videoUrl);
            doorToDoorMediaService.submitVideoAudit(auditRequest);
            return TechnicianResp.success("保存成功", null);
        } catch (Exception e) {
            log.error("转码回调服务异常", e);
        }
        return TechnicianResp.fail(500, "fail");
    }

    @UriMapping(value = "/videoframe/callback")
    public Object videoframeCallback() {
        Cat.logEvent("INVALID_METHOD", "com.dianping.beauty.technician.action.doorToDoor.DoorToDoorUploadAction.videoframeCallback()");
        try {
            //请求
            SwarmCallBackRequest swarmCallBackRequest = getRequest(SwarmCallBackRequest.class);
            if (swarmCallBackRequest == null || swarmCallBackRequest.getStatus() != 0 || StringUtils.isBlank(swarmCallBackRequest.getId())) {
                log.error("调用视频帧截图服务失败，response={}", JSON.toJSONString(swarmCallBackRequest), new DoorToDoorException("转码失败"));
                return TechnicianResp.fail(500, "fail");
            }
            log.info("swarm videoframeCallback request {}", JSON.toJSONString(swarmCallBackRequest));
            String scope = swarmCallBackRequest.getScope();
            String saveAs = swarmCallBackRequest.getSaveAs();
            String videoName = scope.substring(scope.lastIndexOf("/") + 1);
            String picName = saveAs.substring(saveAs.lastIndexOf("/") + 1);
            String videoUrl = "https://" + Lion.getString(Environment.getAppName(), ENDPOINT_LION) + "/" + S3BUCKET_NAME + "/" + videoName;
            String picUrl = "https://" + Lion.getString(Environment.getAppName(), ENDPOINT_LION) + "/" + S3BUCKET_NAME + "/" + picName;
            TechnicianUpdateMediaRequest request = new TechnicianUpdateMediaRequest();
            request.setOriginUrl(videoUrl);
            request.setVideoHeadPic(picUrl);
            TechnicianResp res = doorToDoorMediaService.updateMedia(request);
            if (res == null || res.getCode() != 200) {
                log.error("保存视频封面图服务失败", new DoorToDoorException("保存视频封面失败"));
            }
            return TechnicianResp.success("保存成功", null);
        } catch (Exception e) {
            log.error("调用获取视频帧服务失败", e);
        }
        return TechnicianResp.fail(500, "fail");
    }

    public List<String> getSignature() {
        Cat.logEvent("INVALID_METHOD", "com.dianping.beauty.technician.action.doorToDoor.DoorToDoorUploadAction.getSignature()");
        String bucketName = S3BUCKET_NAME;
        String accessKey = Lion.getString(Environment.getAppName(), ACCESS_KEY_LION);
        String secretKey = Lion.getString(Environment.getAppName(), SECRET_KEY_LION);
        String expiration;
        String signature;
        String policyEncoded;
        String policy;

        // 将date转换为指定格式并设置过期时间为1天后（这个过期时间可以根据自己业务需求自定，应该在满足自己业务需求的前提下尽量小，才更安全）
        Date date = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 1);
        date = calendar.getTime();
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        // 格式如"2018-12-01T00:00:00Z"
        expiration = df.format(date);
        policy = String.format("{\"expiration\":\"%s\",\"conditions\":[{\"bucket\":\"%s\"},[\"starts-with\",\"$key\",\"\"]]}", expiration, bucketName);
        policyEncoded = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
        try {
            signature = Base64.getEncoder().encodeToString(hmacSHA1Encrypt(policyEncoded, secretKey));
        } catch (Exception e) {
            log.error("getNormalUploadSign - 签名生成异常信息 {}, 异常堆栈信息 {}", e.getMessage(), e);
            throw new RuntimeException();
        }
        List<String> list = Lists.newArrayList();
        list.add(signature);
        list.add(policyEncoded);
        list.add(accessKey);
        return list;
    }

    @UriMapping(value = "/uploadtext")
    public Object uploadText() {
        Long serviceOrderId = getLongParameter("serviceOrderId");
        if (serviceOrderId <= 0) {
            return TechnicianResp.fail("参数错误");
        }
        TechnicianResp<TechServiceOrderDTO> serviceOrderResp = null;
        try {
            serviceOrderResp = doorToDoorTechServiceOrderQueryService.findDetailByOrderId(serviceOrderId);
        } catch (Exception e) {
            log.error("upload text query service order fail", e);
        }
        if (serviceOrderResp == null || serviceOrderResp.getData() == null || StringUtils.isEmpty(serviceOrderResp.getData().getExtraInfo())) {
            return TechnicianResp.fail("获取类目发生异常");
        }
        String categoryId = DoorToDoorUtils.getServiceOrderCategory(serviceOrderResp.getData());
        if (StringUtils.isEmpty(categoryId)) {
            return TechnicianResp.fail("获取的类目为空");
        }
        Integer serviceStage = getIntParameter("serviceStage");
        if (serviceStage <= 0) {
            return TechnicianResp.fail("参数错误");
        }
        UploadTextInfo textInfo = new UploadTextInfo();
        textInfo.setExampleText("查看拍摄示例");
        textInfo.setExampleTitle("拍摄示例");
        textInfo.setExampleButtonText("我知道了");
        if (getVersion() >= NEW_VERSION && serviceStage == CLEAN_SELF_OWN_BRAND_UPLOAD_VIDEOS_AFTER_SERVICE) {
            textInfo.setUploadButtonText("上传视频并签出");
        }else {
            textInfo.setUploadButtonText("上传");
        }
        List<UploadTextConfigItem> configItemList = null;
        try {
            configItemList = Lion.getList(Environment.getAppName(), UPLOAD_TEXT_LION, UploadTextConfigItem.class);
        } catch (Exception e) {
            log.error("获取lion配置项technician-mobile-web.doortodoor.upload.text发生异常", e);
        }

        if (CollectionUtils.isEmpty(configItemList)) {
            return TechnicianResp.success(textInfo);
        }

        List<UploadTextInfoItem> textInfoItemList = new ArrayList<>();
        List<UploadTextExampleItem> exampleItemList = new ArrayList<>();
        for (UploadTextConfigItem item : configItemList) {
            //这里面是根据categoryId来获取对应行业的id
            //家电数码维修和家庭维修使用相同的lion配置
            if(!categoryId.equals(APPLIANCE_REPAIR.getId())){
                if(!categoryId.equals(item.getCategoryId()) || (item.getServiceStage()!=null && !serviceStage.equals(item.getServiceStage()))){
                    // serviceStage未配置不continue
                    continue;
                }
            }else{
                //说明是家电维修
                if(!DIGITAL_APPLIANCE_REPAIR.getId().equals(item.getCategoryId()) || (item.getServiceStage()!=null && !serviceStage.equals(item.getServiceStage()))){
                    continue;
                }
            }

            if (EXAMPLE_TYPE.equals(item.getConfigType())) {
                // 配置的是示例，根据configType进行判断
                UploadTextExampleItem exampleItem = new UploadTextExampleItem();
                exampleItem.setTitle(item.getTitle());
                exampleItem.setSubtitle(item.getSubtitle());
                exampleItem.setTip(item.getTip());
                exampleItem.setUrl(item.getUrl());
                exampleItem.setUrls(item.getUrls());
                exampleItem.setVideoCover(item.getVideoCover());
                exampleItem.setType(item.getType());
                exampleItemList.add(exampleItem);
            } else {
                UploadTextInfoItem textInfoItem = new UploadTextInfoItem();
                textInfoItem.setId(item.getId());
                textInfoItem.setTitle(item.getTitle());
                if (CollectionUtils.isNotEmpty(item.getSubtitle())) {
                    textInfoItem.setSubtitle(item.getSubtitle().get(0).getText());
                }
                textInfoItem.setMinUploadFileNum(item.getMinUploadFileNum());
                textInfoItem.setMaxUploadFileNum(item.getMaxUploadFileNum());
                textInfoItem.setType(item.getType());
                textInfoItemList.add(textInfoItem);
            }
        }
        textInfo.setUploadTextInfoItemList(textInfoItemList);
        textInfo.setUploadTextExampleItemList(exampleItemList);
        return TechnicianResp.success(textInfo);
    }

    /**
     * 异常报备上传模版
     * @return
     */
    @UriMapping(value = "/reportExceptionTemplate")
    public Object reportExceptionTemplate() {
        long serviceOrderId = getLongParameter("serviceOrderId");
        try {
            // 校验参数并返回类目id
            String categoryId = checkParamAndReturnCategoryId();
            int techId = getTechId();


            // 查询自检报备
            TechnicianSelfTestRequest request = new TechnicianSelfTestRequest();
            request.setServiceOrderId(String.valueOf(serviceOrderId));
            request.setTechId(techId);
            log.info("reportExceptionTemplate {}", request);
            String version = getStringParameterSafely("version");
            List<ExceptionReportQuestionItem> exceptionReportQuestionItemList = null;
            if (StringUtils.isNotEmpty(version) && version.equals(Lion.getString("technician-mobile-web", INSPECTION_VERSION_KEY, "3"))) {
                TechnicianResp<ExceptionReportQuestionItemDTO> resp = doorToDoorTechnicianSelfTestService.querySelfInspectionReportProblems(request);
                if (resp != null && resp.respSuccess() && resp.getData() != null) {//老老逻辑
                    return buildReportExceptionTemplateResponse(resp.getData());
                }
            }
            // 异常信息为用户不在家时的模板渲染
            TechnicianResp customerNotAtHome = handleCustomerNotAtHomeScenario(serviceOrderId, techId);
            if (customerNotAtHome != null) return customerNotAtHome;

            //异常报备列表渲染 此时服务人员还没有点击上报异常
            if (StringUtils.isNotEmpty(version) && version.equals("4")) {
                // 查模版信息
                exceptionReportQuestionItemList = LionConfigUtil.queryExceptionReportQuestionItemListNew();
            }
            // 组装结果
            return buildReportExceptionTemplateResponse(categoryId, exceptionReportQuestionItemList);
        } catch (Exception e) {
            log.error("reportExceptionTemplate error, serviceOrderId={}", serviceOrderId, e);
            return TechnicianResp.fail("系统繁忙,请稍后再试");
        }
    }

    private TechnicianResp handleCustomerNotAtHomeScenario(long serviceOrderId, int techId){
        DoorToDoorReportExceptionQueryRequest doorToDoorReportExceptionQueryRequest = new DoorToDoorReportExceptionQueryRequest();
        ServiceOrderTechIdPair serviceOrderTechIdPair = new ServiceOrderTechIdPair();
        serviceOrderTechIdPair.setServiceOrderId(serviceOrderId);
        serviceOrderTechIdPair.setTechId(techId);
        List<ServiceOrderTechIdPair> serviceOrderTechIdPairList = Lists.newArrayList();
        serviceOrderTechIdPairList.add(serviceOrderTechIdPair);
        doorToDoorReportExceptionQueryRequest.setServiceOrderTechIdPairList(serviceOrderTechIdPairList);
        TechnicianResp<Boolean> customerNotAtHome = doorToDoorTechnicianReportExceptionService.isCustomerNotAtHome(doorToDoorReportExceptionQueryRequest);
        String keyLast = CATEGORY_VALUE + serviceOrderId;
        StoreKey storeKey = new StoreKey(CATEGORY, keyLast);
        Boolean isStart = false;
        String o = null;
        try {
            o = retryer.call(() -> redisStoreClient.get(storeKey));
        } catch (Exception e) {
            log.error("redisStoreClient.get error",e);
        }
        if(o!=null && o.equals("true")){
            isStart=true;
        }
        if (customerNotAtHome != null && customerNotAtHome.respSuccess() && customerNotAtHome.getData() != null && customerNotAtHome.getData()==true && !isStart) {
            UploadReportExceptionInfoDTO uploadReportExceptionInfoDTO = new UploadReportExceptionInfoDTO();
            uploadReportExceptionInfoDTO.setPageTitle("请稍等，已短信联系用户");
            uploadReportExceptionInfoDTO.setTextDesc("如需取消订单，可点击下方按钮操作");

            TechnicianResp<ServiceOrderDTO> resp = doorToDoorServiceBcpService.queryServiceOrderInfo(serviceOrderId);
            if(resp.respFail()||resp.getData()==null){
                return TechnicianResp.fail(UploadReportExceptionInfoCodeEnum.Not_Successs.getCode(), "订单预定时间获取失败，请重试");
            }
            ServiceOrderDTO serviceOrderDTO = resp.getData();
            if(serviceOrderDTO.getBookStartTime()==null){
                return TechnicianResp.fail(UploadReportExceptionInfoCodeEnum.Not_Successs.getCode(),"订单预定时间获取失败，请重试");
            }
            Date bookStartTime = serviceOrderDTO.getBookStartTime();
            Date currentTime = new Date();
            // 计算保留时间
            Date reserveTime;
            if (currentTime.before(new Date(bookStartTime.getTime() + 30 * 60 * 1000))) {
                // 当前时间在预约开始时间+30分钟内
                reserveTime = new Date(bookStartTime.getTime() + 30 * 60 * 1000);
            } else {
                // 当前时间在预约开始时间+30分钟后
                reserveTime = new Date(currentTime.getTime() + 10 * 60 * 1000);
            }

            uploadReportExceptionInfoDTO.setReserveTime(reserveTime);

            StoreKey storeKeyTime = new StoreKey(CATEGORY, serviceOrderId);
            Date reserveTimeRes = null;
            try {
                reserveTimeRes = dateRetryer.call(()->redisStoreClient.get(storeKeyTime));
            } catch (Exception e) {
                log.error("reportExceptionTemplate get reserveTimeRes error", e);
            }
            if(reserveTimeRes!=null){
                reserveTime = reserveTimeRes;
            }
            uploadReportExceptionInfoDTO.setReserveTime(reserveTime);

            uploadReportExceptionInfoDTO.setTextDesc("若 {reserveTime} 后仍未与用户取得联系，可点击下方按钮取消订单；若联系上用户可正常开始服务，请点击右下方按钮开始服务");

            List<ReportExceptionButtonDTO> buttonList = Lists.newArrayList();
            ReportExceptionButtonDTO reportExceptionButtonDTO1 = new ReportExceptionButtonDTO();
            reportExceptionButtonDTO1.setText("取消订单");
            reportExceptionButtonDTO1.setType("cancel");
            buttonList.add(reportExceptionButtonDTO1);
            ReportExceptionButtonDTO reportExceptionButtonDTO2 = new ReportExceptionButtonDTO();
            reportExceptionButtonDTO2.setText("开始服务");
            reportExceptionButtonDTO2.setType("detail");
            reportExceptionButtonDTO2.setIsRecord(true);
            buttonList.add(reportExceptionButtonDTO2);
            uploadReportExceptionInfoDTO.setButton(buttonList);

            uploadReportExceptionInfoDTO.setHasReportedCustomerNotAtHome(true);

            uploadReportExceptionInfoDTO.setReportId(SubmitAfterCheckInEnum.CustomerNotAtHome.getCode());

            return TechnicianResp.success(uploadReportExceptionInfoDTO);
        }
        return null;
    }
    private TechnicianResp<ReportExceptionTemplateResponse> buildReportExceptionTemplateResponse(ExceptionReportQuestionItemDTO data) {
        ReportExceptionTemplateResponse response = new ReportExceptionTemplateResponse();
        response.setTitle("自检报备");
        buildReportIssueModule(data,response);
        buildEvidencePictureModule(response);
        buildIssueReasonModule(response);
        response.setReportType(ReportTypeEnum.INSPECTION_REPORT.getType());
        log.info("自检报备 :{}", response);
        return TechnicianResp.success(response);
    }

    private void buildReportIssueModule(ExceptionReportQuestionItemDTO data,ReportExceptionTemplateResponse response) {
        List<ReportIssueDTO> reportIssueDTOS = data.getReportIssueDTOS();
        ReportIssueModule reportIssueModule = new ReportIssueModule();
        reportIssueModule.setTitle("请选择您需要报备的问题（单选）");
        List<ReportIssue> list = new ArrayList<>();
        for (ReportIssueDTO reportIssueDTO : reportIssueDTOS) {
            ReportIssue reportIssue = new ReportIssue();
            BeanUtils.copyProperties(reportIssueDTO,reportIssue);
            List<ReportIssueDTO.SubReportIssueDTO> subReportIssueDTOS = reportIssueDTO.getSubReportIssueDTOS();
            if(CollectionUtils.isNotEmpty(subReportIssueDTOS)){
                reportIssue.setSubReportIssueDTOS(subReportIssueDTOS);
            }
            list.add(reportIssue);
        }
        reportIssueModule.setReportIssueList(list);
        response.setReportIssueModule(reportIssueModule);
    }

    /**
     * 取消订单
     */
    @UriMapping(value = "/cancelOrder")
    @Transactional(rollbackFor = Exception.class)
    public Object cancelOrder() {
        CancelOrderExceptionRequest request = getRequest(CancelOrderExceptionRequest.class);
        CancelOrderDTO cancelOrderDTO = new CancelOrderDTO();
        try {
            // 校验参数并返回类目id
            Boolean checkResult = checkParamCancelOrder(request);
            if(!checkResult){
                return TechnicianResp.fail("参数错误");
            }
            Integer techId = getTechId();

            // 上传其他信息 客户不在家时
            uploadOrderInfo(request,techId,cancelOrderDTO);

            if(!request.getCancel()){//  不取消订单
                String keyLast = "status" + request.getServiceOrderId();
                StoreKey storeKey = new StoreKey(CATEGORY, keyLast);
                String value = "true";
                try {
                    redisStoreClient.set(storeKey, value);
                } catch (Exception e) {
                    log.error("reportExceptionTemplate set reserveTimeRes error", e);
                }
                return TechnicianResp.success(cancelOrderDTO);
            }

            // 发送短信
            Boolean isSend = sendMessageToCustomer(request, CancelAfterSubmitEnum.fromCode(request.getId()), cancelOrderDTO);

            // 取消订单
            boolean isSuccessCancel = cancelOrderForException(techId, request,cancelOrderDTO);
            if(!isSuccessCancel){
                throw new RuntimeException("取消订单失败"); // 抛出异常以回滚事务
            }
            // 如果服务超限，生成判责单
            if (request.getId()==SubmitAfterCheckInEnum.ServiceAreaExceedsLimits.getCode()){//服务范围超限
                createLiabilityOrder(request.getServiceOrderId(),cancelOrderDTO);
            }

        }catch (Exception e){
            log.error("系统繁忙,请稍后再试", e);
            throw new RuntimeException("系统繁忙,请稍后再试"); // 确保事务回滚
        }
        return TechnicianResp.success(cancelOrderDTO);
    }

    private boolean cancelOrderForException(Integer techId,CancelOrderExceptionRequest request,CancelOrderDTO cancelOrderDTO) {
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest();
        TakeOrderRecordDetailDTO takeOrderRecord = getTakeOrderRecord(techId, request.getServiceOrderId());
        if(takeOrderRecord==null){
            return false;
        }
        CancelAfterSubmitEnum cancelAfterSubmitEnum = CancelAfterSubmitEnum.fromCode(request.getId());
        if (cancelAfterSubmitEnum == null) {
            return false;
        }
        String reason="劳动者端取消 客户问题-"+cancelAfterSubmitEnum.getDesc();
        cancelOrderRequest.setOrderId(takeOrderRecord.getReserveOrderId());
        cancelOrderRequest.setOperator(ReserveOperatorEnum.System.type);
        cancelOrderRequest.setReason(reason);
        try {
            ReserveResponse<Boolean> response = reserveOrderProcessService.cancelOrder(cancelOrderRequest);
            if(response==null||!response.isSuccess()){
                return false;
            }
        } catch (Exception e) {
            log.error("cancelOrderForException error, cancelOrderRequest={}", cancelOrderRequest, e);
            return false;
        }
        cancelOrderDTO.setIsCancelSuccess(true);
        return true;
    }

    private void uploadOrderInfo(CancelOrderExceptionRequest request, Integer techId, CancelOrderDTO cancelOrderDTO) {
        if(request.getId()!=CancelAfterSubmitEnum.CustomerNotAtHome.getCode()){
            return;
        }
        String notCancelMessage="于 %s 正常开始服务";
        String cancelMessage="于 %s 确认无法服务并离开";
        String time = new SimpleDateFormat("H点mm分").format(new Date());
        BatchInsertReportExceptionOtherInfoRequest batchInsertReportExceptionOtherInfoRequest = new BatchInsertReportExceptionOtherInfoRequest();
        batchInsertReportExceptionOtherInfoRequest.setServiceOrderId(request.getServiceOrderId());
        batchInsertReportExceptionOtherInfoRequest.setTechId(techId);
        if(request.getCancel()){
            batchInsertReportExceptionOtherInfoRequest.setOtherInfo(String.format(cancelMessage,time));
        }else {
            batchInsertReportExceptionOtherInfoRequest.setOtherInfo(String.format(notCancelMessage,time));
        }
        try {
            doorToDoorTechnicianReportExceptionService.batchInsertReportExceptionOtherInfo(batchInsertReportExceptionOtherInfoRequest);
            cancelOrderDTO.setIsGenerateOrderInfo(true);
        } catch (Exception e) {
            log.error("uploadOrderInfo error, request={}", request, e);
        }
    }

    private Boolean sendMessageToCustomer(CancelOrderExceptionRequest request, CancelAfterSubmitEnum cancelAfterSubmitEnum, CancelOrderDTO cancelOrderDTO) {
        TechnicianResp<ServiceOrderDTO> resp = doorToDoorServiceBcpService.queryServiceOrderInfo(request.getServiceOrderId());
        if(resp.respFail()||resp.getData()==null){
            return false;
        }
        ServiceOrderDTO serviceOrderDTO = resp.getData();
        if(serviceOrderDTO.getBookStartTime()==null){
            return false;
        }
        String mobileNo = getMobileNo(request.getServiceOrderId());
        Date bookStartTime = serviceOrderDTO.getBookStartTime();
        log.info("sendMessageToCustomer开始发送短信，reason:{},templateNO:{},mobile:{},request:{}",
                cancelAfterSubmitEnum.getMessage(),Environment.isOfflineEnv() ? 2015094 : 176502,mobileNo,JsonUtils.toJson(request));

        if (StringUtils.isNotEmpty(mobileNo)) {
            String bookStartTimeStr = new SimpleDateFormat("HH:mm").format(bookStartTime);
            try {
                SMSRequest smsRequest = new SMSRequest();
                smsRequest.setMobileNo(mobileNo);
                smsRequest.setTemplateNO(Environment.isOfflineEnv() ? 2015094 : 176502);
                Map<String, String> parameter = Maps.newHashMap();
                parameter.put("reason", cancelAfterSubmitEnum.getMessage());
                parameter.put("bookStartTime", bookStartTimeStr);
                smsRequest.setParameter(parameter);
                technicianSendMessageService.asynSendSMS(smsRequest);
                cancelOrderDTO.setIsSendMessage(true);
                log.info("sendMessageToCustomer短信发送成功，reason:{},bookStartTime:{},templateNO:{},mobile:{},request:{}",
                        cancelAfterSubmitEnum.getMessage(),bookStartTimeStr,Environment.isOfflineEnv() ? 2015094 : 176502,mobileNo,JsonUtils.toJson(request));
            } catch (Exception e) {
                log.error("sendMessageToCustomer短信发送失败，reason:{},bookStartTime:{},templateNO:{},mobile:{},request:{}",
                        cancelAfterSubmitEnum.getMessage(),bookStartTimeStr,Environment.isOfflineEnv() ? 2015094 : 176502,mobileNo,JsonUtils.toJson(request),e);
            }
        }
        return true;
    }

    private String getMobileNo(Long serviceOrderId){
        if (serviceOrderId != null) {
            //服务单id查询服务单信息
            TechnicianResp<TechServiceOrderDTO> serviceOrderDTOTechnicianResp = doorToDoorTechServiceOrderQueryService.findDetailByOrderId(serviceOrderId);
            if (serviceOrderDTOTechnicianResp.respFail() || serviceOrderDTOTechnicianResp.getData() == null) {
                return "";
            }
            return serviceOrderDTOTechnicianResp.getData().getUserMobileNo();
        }
        return "";
    }

    private Boolean checkParamCancelOrder(CancelOrderExceptionRequest request) {
        if(request==null
                || request.getServiceOrderId()==null
                || request.getServiceOrderId()<=0
                || request.getId()==null
                || request.getId()<=0
                ||request.getCancel()==null){
            return false;
        }
        boolean flag=false;

        CancelAfterSubmitEnum cancelAfterSubmitEnum = CancelAfterSubmitEnum.fromCode(request.getId());
        if(cancelAfterSubmitEnum==null){
            return false;
        }
        for(CancelAfterSubmitEnum cancelAfterSubmitEnum1:CancelAfterSubmitEnum.values()){
            if (cancelAfterSubmitEnum1.getCode() == request.getId()) {
                flag = true;
                break;
            }
        }
        if(!flag){
            return false;
        }
        return true;
    }

    /**
     *  异常报备信息上传
     * @return
     */
    @UriMapping(value = "/uploadReportExceptionInfo")
    public Object uploadReportExceptionInfo() {
        UploadReportExceptionInfoRequest request = getRequest(UploadReportExceptionInfoRequest.class);
        try {
            // 校验参数
            String checkFailText = checkUploadReportExceptionInfoParam(request);
            if (StringUtils.isNotEmpty(checkFailText)) {
                return TechnicianResp.fail(checkFailText);
            }

            if(StringUtils.isNotEmpty(request.getVersion())&&request.getVersion().equals("1")){//新版本
                int techId = getTechId();
                boolean isSignFlag = false;
                boolean isSendMsgFlag = false;
                boolean isCancelFlag = false;
                boolean isContact = false;
                UploadReportExceptionInfoDTO uploadReportExceptionInfoDTO = new UploadReportExceptionInfoDTO();
                uploadReportExceptionInfoDTO.setPageTitle("已报备完成");
                List<ReportExceptionButtonDTO> button = Lists.newArrayList();
                ReportExceptionButtonDTO buttonDTO = new ReportExceptionButtonDTO();
                buttonDTO.setText("返回订单详情页");
                buttonDTO.setType("detail");
                button.add(buttonDTO);
                uploadReportExceptionInfoDTO.setButton(button);
                for(SubmitAfterCheckInEnum submitAfterCheckInEnum:SubmitAfterCheckInEnum.values()){//判断是否需要签到
                    if(submitAfterCheckInEnum.getCode()==request.getId()){
                        isSignFlag = true;
                        break;
                    }
                }
                for(SendMessageAfterSubmitEnum sendMessageAfterSubmitEnum:SendMessageAfterSubmitEnum.values()){//判断是否需要触达站长
                    if(sendMessageAfterSubmitEnum.getCode()==request.getId()){
                        isSendMsgFlag = true;
                        break;
                    }
                }
                for(CancelAfterSubmitEnum cancelAfterSubmitEnum:CancelAfterSubmitEnum.values()){//判断是否需要取消订单
                    if(cancelAfterSubmitEnum.getCode()==request.getId()){
                        isCancelFlag = true;
                        break;
                    }
                }
                for (ContactCustomerEnum contactCustomerEnum : ContactCustomerEnum.values()) {
                    if (contactCustomerEnum.getCode() == request.getId()) {
                        isContact = true;
                        break;
                    }
                }
                if(isContact){
                    uploadReportExceptionInfoDTO.setTextDesc("请您主动联系用户说明情况");
                    List<ReportExceptionButtonDTO> buttonList = Lists.newArrayList();
                    ReportExceptionButtonDTO reportExceptionButtonDTO = new ReportExceptionButtonDTO();
                    reportExceptionButtonDTO.setText("联系用户说明情况");
                    reportExceptionButtonDTO.setType("detail");
                    buttonList.add(reportExceptionButtonDTO);
                    uploadReportExceptionInfoDTO.setButton(buttonList);
                }
                if(isCancelFlag){
                    uploadReportExceptionInfoDTO.setTextDesc("如需取消订单，可点击下方按钮操作");
                    List<ReportExceptionButtonDTO> buttonList = Lists.newArrayList();
                    ReportExceptionButtonDTO reportExceptionButtonDTO1 = new ReportExceptionButtonDTO();
                    reportExceptionButtonDTO1.setText("取消订单");
                    reportExceptionButtonDTO1.setType("cancel");
                    buttonList.add(reportExceptionButtonDTO1);
                    ReportExceptionButtonDTO reportExceptionButtonDTO2 = new ReportExceptionButtonDTO();
                    reportExceptionButtonDTO2.setText("继续服务");
                    reportExceptionButtonDTO2.setType("detail");
                    reportExceptionButtonDTO2.setIsRecord(true);
                    buttonList.add(reportExceptionButtonDTO2);
                    uploadReportExceptionInfoDTO.setButton(buttonList);
                }
                if(isSignFlag){//需要签到
                    boolean hasSignOrder = doorToDoorTaskCenterService.hasSignOrder(request.getServiceOrderId());
                    if(!hasSignOrder){
                        uploadReportExceptionInfoDTO.setPageTitle("请先完成签到");
                        uploadReportExceptionInfoDTO.setTextDesc("完成签到后，你可以继续进行异常报备");
                        List<ReportExceptionButtonDTO> buttonList = Lists.newArrayList();
                        ReportExceptionButtonDTO reportExceptionButtonDTO = new ReportExceptionButtonDTO();
                        reportExceptionButtonDTO.setText("已到达，立即签到");
                        reportExceptionButtonDTO.setType("detail");
                        buttonList.add(reportExceptionButtonDTO);
                        uploadReportExceptionInfoDTO.setButton(buttonList);
                        return new TechnicianResp(UploadReportExceptionInfoCodeEnum.Not_Check_In.getCode(), "请先完成签到",uploadReportExceptionInfoDTO);
                    }
                }
                if(request.getId()==SubmitAfterCheckInEnum.CustomerNotAtHome.getCode()){//顾客不在家
                    TechnicianResp<ServiceOrderDTO> resp = doorToDoorServiceBcpService.queryServiceOrderInfo(request.getServiceOrderId());
                    if(resp.respFail()||resp.getData()==null){
                        return TechnicianResp.fail(UploadReportExceptionInfoCodeEnum.Not_Successs.getCode(), "订单预定时间获取失败，请重试");
                    }
                    ServiceOrderDTO serviceOrderDTO = resp.getData();
                    if(serviceOrderDTO.getBookStartTime()==null){
                        return TechnicianResp.fail(UploadReportExceptionInfoCodeEnum.Not_Successs.getCode(),"订单预定时间获取失败，请重试");
                    }
                    Date bookStartTime = serviceOrderDTO.getBookStartTime();
                    Date currentTime = new Date();
                    // 计算保留时间
                    Date reserveTime;
                    if (currentTime.before(new Date(bookStartTime.getTime() + 30 * 60 * 1000))) {
                        // 当前时间在预约开始时间+30分钟内
                        reserveTime = new Date(bookStartTime.getTime() + 30 * 60 * 1000);
                    } else {
                        // 当前时间在预约开始时间+30分钟后
                        reserveTime = new Date(currentTime.getTime() + 10 * 60 * 1000);
                    }

                    uploadReportExceptionInfoDTO.setReserveTime(reserveTime);

                    String key = CATEGORY_VALUE+request.getServiceOrderId();
                    StoreKey storeKeyTime = new StoreKey(CATEGORY, request.getServiceOrderId());
                    StoreKey storeKey = new StoreKey(CATEGORY, key);
                    try {
                        setRetryer.call(()->{
                            redisStoreClient.set(storeKeyTime,reserveTime);
                            redisStoreClient.delete(storeKey);
                            return null;
                        });
                    } catch (Exception e) {
                        log.error("redisStoreClient.set error",e);
                    }
                    uploadReportExceptionInfoDTO.setTextDesc("若 {reserveTime} 后联系不上用户，可点击取消订单；若联系上客户，请点击开始服务");
                    List<ReportExceptionButtonDTO> buttonList = Lists.newArrayList();
                    ReportExceptionButtonDTO reportExceptionButtonDTO1 = new ReportExceptionButtonDTO();
                    reportExceptionButtonDTO1.setText("取消订单");
                    reportExceptionButtonDTO1.setType("cancel");
                    buttonList.add(reportExceptionButtonDTO1);
                    ReportExceptionButtonDTO reportExceptionButtonDTO2 = new ReportExceptionButtonDTO();
                    reportExceptionButtonDTO2.setText("开始服务");
                    reportExceptionButtonDTO2.setType("detail");
                    reportExceptionButtonDTO2.setIsRecord(true);
                    buttonList.add(reportExceptionButtonDTO2);
                    uploadReportExceptionInfoDTO.setButton(buttonList);

                    String mobileNo = getMobileNo(request.getServiceOrderId());

                    log.info("uploadReportExceptionInfo短信开始发送,mobileNo:{},templateNO:{},request:{}",mobileNo,
                            Environment.isOfflineEnv() ? 2015087 : 176343,JsonUtils.toJson(request));

                    if (StringUtils.isNotEmpty(mobileNo)) {
                        String reserveTimeStr = new SimpleDateFormat("HH:mm").format(reserveTime);
                        String bookStartTimeStr = new SimpleDateFormat("HH:mm").format(bookStartTime);
                        try {
                            SMSRequest smsRequest = new SMSRequest();
                            smsRequest.setMobileNo(mobileNo);
                            smsRequest.setTemplateNO(Environment.isOfflineEnv() ? 2015087 : 176343);
                            Map<String, String> parameter = Maps.newHashMap();
                            parameter.put("reserveTime", reserveTimeStr);
                            parameter.put("bookStartTime", bookStartTimeStr);
                            smsRequest.setParameter(parameter);
                            technicianSendMessageService.asynSendSMS(smsRequest);
                            uploadReportExceptionInfoDTO.setSmsSendSuccess(true);
                            uploadReportExceptionInfoDTO.setPageTitle("请稍等，已短信联系用户");
                            log.info("uploadReportExceptionInfo短信发送成功,mobileNo:{},bookStartTime:{},templateNO:{},request:{}",mobileNo,
                                    bookStartTimeStr,Environment.isOfflineEnv() ? 2015087 : 176343,JsonUtils.toJson(request));
                        } catch (Exception e) {
                            log.error("uploadReportExceptionInfo短信发送失败,mobileNo:{},bookStartTime:{},templateNO:{},request:{}",mobileNo,
                                    bookStartTimeStr,Environment.isOfflineEnv() ? 2015087 : 176343,JsonUtils.toJson(request),e);
                        }
                    }
                }
                if(isSendMsgFlag){//需要触达站长
                    SendMessageAfterSubmitEnum sendMessageAfterSubmitEnum = SendMessageAfterSubmitEnum.fromCode(request.getId());
                    sendMessageForMaster(request,techId, sendMessageAfterSubmitEnum, uploadReportExceptionInfoDTO);
                }
                // 上传图片/视频/文本
                TechnicianResp<Boolean> technicianReportExceptionResp = uploadMediaAndReason(request);
                if (technicianReportExceptionResp == null || technicianReportExceptionResp.respFail()) {
                    return TechnicianResp.fail(UploadReportExceptionInfoCodeEnum.Not_Successs.getCode(),"异常报备上传失败");
                }
                return new TechnicianResp(UploadReportExceptionInfoCodeEnum.Success.getCode(),UploadReportExceptionInfoCodeEnum.Success.getDesc(),uploadReportExceptionInfoDTO);
            }

            // 上传图片/视频/文本
            TechnicianResp<Boolean> technicianReportExceptionResp = uploadMediaAndReason(request);
            if (technicianReportExceptionResp == null || technicianReportExceptionResp.respFail()) {
                return TechnicianResp.fail("异常报备上传失败");
            }
            return TechnicianResp.success(true);
        } catch (BizException e) {
            return TechnicianResp.fail(e.getMessage());
        } catch (Exception e) {
            log.error("uploadReportExceptionInfo error, request={}", JsonUtils.toJson(request), e);
            return TechnicianResp.fail("系统繁忙,请稍后再试");
        }
    }

    private void sendMessageForMaster(UploadReportExceptionInfoRequest request,Integer techId, SendMessageAfterSubmitEnum sendMessageAfterSubmitEnum, UploadReportExceptionInfoDTO uploadReportExceptionInfoDTO) {
        if(techId==null||techId<=0||sendMessageAfterSubmitEnum==null||uploadReportExceptionInfoDTO==null){
            return;
        }
        String masterMis = "";
        masterMis = getMasterMis(techId);
        TextBody message = new TextBody();
        String name = "";
        name = doorToDoorTaskCenterService.queryNameByTechId(techId);

        String reverseOrderIdAndTime = "";
        TechnicianResp<ServiceOrderDTO> resp = doorToDoorServiceBcpService.queryServiceOrderInfo(request.getServiceOrderId());
        if(resp.respFail()||resp.getData()==null){
            return;
        }
        ServiceOrderDTO serviceOrderDTO = resp.getData();
        if(serviceOrderDTO.getBookStartTime()==null){
            return;
        }
        Date bookStartTime = serviceOrderDTO.getBookStartTime();
        String bookStartTimeStr = new SimpleDateFormat("MM:dd:HH:mm").format(bookStartTime);
        TakeOrderRecordDetailDTO takeOrderRecord = getTakeOrderRecord(techId, request.getServiceOrderId());
        if (takeOrderRecord == null||takeOrderRecord.getReserveOrderId()==null) {
            return;
        }
        reverseOrderIdAndTime= takeOrderRecord.getReserveOrderId() + "(" + bookStartTimeStr +")";

        String url = "";
        String strFormat = Lion.getString("technician-mobile-web", "technician-mobile-web.haiXing.order.url");
        url= String.format(strFormat, takeOrderRecord.getReserveOrderId().toString());

        message.setText(String.format(sendMessageAfterSubmitEnum.getMessage(),
                name,
                reverseOrderIdAndTime,
                url));
        if(StringUtils.isNotEmpty(masterMis)){
            commonPushBaseService.newBeePush3(message, Collections.singletonList(masterMis), Pusher.LIFE_SERVICE_STARFISH_PLATFORM);
            uploadReportExceptionInfoDTO.setSendMasterSuccess(true);
        }
    }

    private String getMasterMis(Integer techId) {
        if(techId==null||techId<=0){
            return "";
        }
        String masterMis = "";
        SpTechnicianQueryRequest request = new SpTechnicianQueryRequest();
        request.setTechnicianId(Long.valueOf(techId));
        Response<List<SpTechnicianDTO>> listSpResponse = null;
        try {
            listSpResponse = spTechnicianDomainService.queryTechniciansFromDB(request);
        } catch (Exception e) {
            log.error("getCooperationType-error,techId={}", techId, e);
        }
        SpTechnicianDTO spTechnicianDTO = null;
        if (listSpResponse != null && CollectionUtils.isNotEmpty(listSpResponse.getData())) {
            spTechnicianDTO = listSpResponse.getData().get(0);
        }
        if(spTechnicianDTO == null){
            log.error("查询手艺人合作关系失败, techId is {}", techId);
            return "";
        }
        Integer cooperationType = spTechnicianDTO.getCooperationType();
        if(cooperationType==null){
            return "";
        }
        TechnicianAreaRelationDTO technicianAreaRelationDTO = null;
        if (cooperationType == CooperationTypeEnum.CROWD_SOURCING.getCode()) {
            QueryTechnicianAreaRelationReq req = new QueryTechnicianAreaRelationReq();
            req.setTechnicianId(Long.valueOf(techId));
            Response<List<TechnicianAreaRelationDTO>> listResponse = null;
            try {
                listResponse = spTechnicianDomainService.queryTechnicianAreaRelation(req);
            }catch (Exception e) {
                log.error("查询手艺人区域关系失败, techId is {}", techId, e);
            }
            if (listResponse == null || !listResponse.isSuccess() || CollectionUtils.isEmpty(listResponse.getData())) {
                log.error("查询手艺人区域关系失败, techId is {}", techId);
                return "";
            }
            List<TechnicianAreaRelationDTO> data = listResponse.getData();
            technicianAreaRelationDTO = data.get(0);
            if (technicianAreaRelationDTO == null) {
                log.error("查询手艺人区域关系失败, techId is {}", techId);
                return "";
            }
            masterMis = technicianAreaRelationDTO.getMis();
        }
        if(cooperationType==CooperationTypeEnum.SERVICE_PROVIDER.getCode()){
            try {
                Response<ServiceProviderDTO> serviceProviderDTOResponse = serviceProviderQueryService.loadServiceProvider(spTechnicianDTO.getProviderId());
                if (serviceProviderDTOResponse == null || !serviceProviderDTOResponse.isSuccess() || serviceProviderDTOResponse.getData() == null) {
                    log.error("查询服务商失败, techId is {}", techId);
                    return "";
                }
                masterMis = serviceProviderDTOResponse.getData().getAdminAccountName();
            } catch (TException e) {
                log.error("查询服务商失败, techId is {}", techId, e);
                return "";
            }

        }
        return masterMis;
    }

    /**
     * 发起判责单
     * @param serviceOrderId
     * @return
     */
    private void createLiabilityOrder(long serviceOrderId,CancelOrderDTO cancelOrderDTO){
        if(serviceOrderId<=0){
            return;
        }
        try {
            int techId = getTechId();
            TakeOrderRecordDetailDTO takeOrderRecord = getTakeOrderRecord(techId, serviceOrderId);
            if (takeOrderRecord == null) {
                throw new RuntimeException("获取接单记录失败");
            }
            Long reserveOrderId = takeOrderRecord.getReserveOrderId();
            Integer takeOrderRecordId = takeOrderRecord.getTakeOrderId();
            String techName = doorToDoorTaskCenterService.queryNameByTechId(techId);

            CreateOrderAndAttrRequest createOrderAndAttrRequest = new CreateOrderAndAttrRequest();
            createOrderAndAttrRequest.setReserveOrderId(reserveOrderId);
            createOrderAndAttrRequest.setTakeOrderRecordId(Long.valueOf(takeOrderRecordId));
            createOrderAndAttrRequest.setTechName(techName);
            createOrderAndAttrRequest.setWorkOrderSource(WorkOrderSourceEnum.workerReport.getCode());

            Map<String, String> attrs = new HashMap<>();
            buildAttrs(attrs, techId);
            createOrderAndAttrRequest.setAttrs(attrs);

            TechnicianResp<String> result = assessLiabilityOrderProcessService.createOrderAndAttrByReserveOrderId(createOrderAndAttrRequest);
            if (result == null||result.respFail() || result.getData() == null) {
                log.error("提交工单失败, result is null,techId{},serviceOrderId{}",techId,serviceOrderId);
                throw new RuntimeException("提交工单失败");
            }
        } catch (Exception e) {
            log.error("提交工单失败", e);
            throw new RuntimeException("提交工单失败");
        }
        cancelOrderDTO.setIsCancelSuccess(true);
    }

    private void buildAttrs(Map<String, String> attrs, Integer techId) {
        WorkOrderProblemDTO workOrderProblemDTO = new WorkOrderProblemDTO(1,"工单来源",1,Collections.singletonList((new WorkOrderProblemChoiceDTO(WorkOrderSourceEnum.workerReport.getCode(), WorkOrderSourceEnum.workerReport.getDesc()))));
        List<WorkOrderProblemDTO> problems = new ArrayList<>();
        problems.add(new WorkOrderProblemDTO(1, "高危问题", 2, Collections.emptyList()));
        problems.add(new WorkOrderProblemDTO(WorkOrderProblemEnum.servicePersonnelDeclinesOrder.getCategoryId(),
                WorkOrderProblemEnum.servicePersonnelDeclinesOrder.getCategoryName(),
                2,
                Collections.singletonList(new WorkOrderProblemChoiceDTO(WorkOrderProblemEnum.servicePersonnelDeclinesOrder.getId(),
                        WorkOrderProblemEnum.servicePersonnelDeclinesOrder.getDesc()))));
        problems.add(new WorkOrderProblemDTO(3, "售后体验问题", 2, Collections.emptyList()));
        attrs.put(AssessAttrKeyEnum.WORKORDER_SOURCE.getAttrKey(), JsonUtils.toJson(workOrderProblemDTO));
        attrs.put(AssessAttrKeyEnum.WORKORDER_PROBLEM.getAttrKey(), JsonUtils.toJson(problems));
        attrs.put(AssessAttrKeyEnum.PROBLEM_DESC.getAttrKey(), "");
        attrs.put(AssessAttrKeyEnum.OPERATOR_ID.getAttrKey(), "系统判责");
        attrs.put(AssessAttrKeyEnum.TECH_ID.getAttrKey(), String.valueOf(techId));
        putStationMasterMis(attrs);
    }

    private void putStationMasterMis(Map<String, String> attrs) {
        if (MapUtils.isEmpty(attrs) || StringUtils.isBlank(attrs.get(AssessAttrKeyEnum.TECH_ID.getAttrKey()))) {
            return;
        }
        String techIdStr = attrs.get(AssessAttrKeyEnum.TECH_ID.getAttrKey());
        long techId = Long.parseLong(techIdStr);
        QueryTechnicianAreaRelationReq req = new QueryTechnicianAreaRelationReq();
        req.setTechnicianId(techId);
        Response<List<TechnicianAreaRelationDTO>> listResponse = null;
        try {
            listResponse = spTechnicianDomainService.queryTechnicianAreaRelation(req);
        }catch (Exception e) {
            log.error("查询手艺人区域关系失败, techId is {}", techId, e);
        }
        if (listResponse == null || !listResponse.isSuccess() || org.apache.commons.collections.CollectionUtils.isEmpty(listResponse.getData())) {
            log.error("查询手艺人区域关系失败, techId is {}", techId);
            return;
        }
        List<TechnicianAreaRelationDTO> data = listResponse.getData();
        TechnicianAreaRelationDTO technicianAreaRelationDTO = data.get(0);
        if (technicianAreaRelationDTO == null) {
            log.error("查询手艺人区域关系失败, techId is {}", techId);
            return;
        }
        SpTechnicianQueryRequest request = new SpTechnicianQueryRequest();
        request.setTechnicianId(techId);
        Response<List<SpTechnicianDTO>> listSpResponse = null;
        try {
            listSpResponse = spTechnicianDomainService.queryTechniciansFromDB(request);
        } catch (Exception e) {
            log.error("buildCooperationType-error,techId={}", techId, e);
        }
        SpTechnicianDTO spTechnicianDTO = null;
        if (listSpResponse != null && org.apache.commons.collections.CollectionUtils.isNotEmpty(listSpResponse.getData())) {
            spTechnicianDTO = listSpResponse.getData().get(0);
        }
        if(spTechnicianDTO == null){
            log.error("查询手艺人合作关系失败, techId is {}", techId);
            return;
        }
        try {
            attrs.put(AssessAttrKeyEnum.COOPERATE_TYPE.getAttrKey(), String.valueOf(spTechnicianDTO.getCooperationType()));
            attrs.put(AssessAttrKeyEnum.STATION_MASTER_MIS.getAttrKey(), technicianAreaRelationDTO.getMis());
            attrs.put(AssessAttrKeyEnum.AREA_ID.getAttrKey(), String.valueOf(technicianAreaRelationDTO.getAreaId()));
        } catch (Exception e) {
            log.error("attrs put error, techId is {}", techId, e);
        }
    }

    //查询服务人员对应预约单
    private TakeOrderRecordDetailDTO getTakeOrderRecord(int techId, long serviceOrderId) {
        TechnicianResp<TakeOrderRecordDetailDTO> recordDetailRes = doorToDoorTakeOrderRecordService.findRecordDetail(techId, serviceOrderId);
        if (recordDetailRes.respFail()) {
            throw new BizException(300, "查询接单记录错误");
        }

        if (recordDetailRes.getData() == null) {
            //如果查询不到接单记录,就插入一条待接单的记录
            TakeOrderRecordCreateDTO record = new TakeOrderRecordCreateDTO();
            record.setTechId(techId);
            record.setServiceOrderId(serviceOrderId);
            TechnicianResp<Integer> technicianResp = doorToDoorTakeOrderRecordService.createOrderRecord(record);
            TakeOrderRecordDetailDTO orderRecord = new TakeOrderRecordDetailDTO();
            orderRecord.setServiceOrderId(serviceOrderId);
            orderRecord.setRecordStatus(DoorToDoorTakeOrderRecordStatus.WAIT_TAKEORDER);
            orderRecord.setTakeOrderId(technicianResp != null && technicianResp.respSuccess() ? technicianResp.getData() : null);
            return orderRecord;
        }

        return recordDetailRes.getData();
    }


    private String checkUploadReportExceptionInfoParam(UploadReportExceptionInfoRequest request){
        if (request == null || request.getServiceOrderId() <= 0 || request.getId() <= 0) {
            throw new BizException("入参错误") ;
        }
        request.setTechId(getTechId());
        if (request.getTechId() <= 0) {
            throw new BizException("未登陆") ;
        }
        //校验服务单和手艺人是否匹配
        DoorToDoorTakeOrderRecordDTO doorToDoorTakeOrderRecordDTO = getDoorToDoorTakeOrderRecordDTO(request);
        if (doorToDoorTakeOrderRecordDTO == null) {
            throw new BizException("接单查询失败") ;
        }
        // 查类目信息
        String categoryId = getCategoryId(request.getServiceOrderId());
        if (StringUtils.isEmpty(categoryId)) {
            throw new BizException("获取类目失败") ;
        }
        // 类目是否支持异常报备
        if (!LionConfigUtil.checkCategorySupportReportException(categoryId)){
            throw new BizException("当前类目不支持异常报备") ;
        }
        // 当前上传内容是否符合模版要求
        if(StringUtils.isNotEmpty(request.getVersion())&&request.getVersion().equals("1")){
            return checkTemplateComplianceNew(request, categoryId);
        }
        return checkTemplateCompliance(request, categoryId);
    }

    private TechnicianResp<Boolean> uploadMediaAndReason(UploadReportExceptionInfoRequest request) {
        TechnicianResp<Boolean> technicianReportExceptionResp = null;
        try{
            technicianReportExceptionResp = doorToDoorTechnicianReportExceptionService.batchInsertReportException(adaptBatchInsertReportExceptionRequest(request));
        }catch (Exception e){
            log.error("uploadReportExceptionInfo batchInsertReportException error, request:{}", JsonUtils.toJson(request), e);
        }
        return technicianReportExceptionResp;
    }

    private BatchInsertReportExceptionRequest adaptBatchInsertReportExceptionRequest(UploadReportExceptionInfoRequest request) {
        BatchInsertReportExceptionRequest batchInsertReportExceptionRequest = new BatchInsertReportExceptionRequest();
        batchInsertReportExceptionRequest.setId(request.getId());
        batchInsertReportExceptionRequest.setReason(request.getReason());
        batchInsertReportExceptionRequest.setTechId(request.getTechId());
        batchInsertReportExceptionRequest.setServiceOrderId(request.getServiceOrderId());
        batchInsertReportExceptionRequest.setUserId(getLoginUserIdForMultiPlatform());
        batchInsertReportExceptionRequest.setReportTime(new Date().getTime());

        List<TechnicianMediaDTO> mediaList = Lists.newArrayList();
        List<ReportExceptionMedia> mediaUrlList = request.getMediaUrlList();
        if (CollectionUtils.isNotEmpty(mediaUrlList)) {
            for (ReportExceptionMedia reportExceptionMedia : mediaUrlList) {
                TechnicianMediaDTO technicianMediaDTO = new TechnicianMediaDTO();
                technicianMediaDTO.setMediaType(reportExceptionMedia.getMediaType());
                technicianMediaDTO.setFilename(reportExceptionMedia.getFilename());
                technicianMediaDTO.setMediaUrl(reportExceptionMedia.getMediaUrl());
                technicianMediaDTO.setServiceStage(ServiceStageEnum.REPORT_EXCEPTION.getType());
                mediaList.add(technicianMediaDTO);
            }
        }
        batchInsertReportExceptionRequest.setMediaUrlList(mediaList);
        return batchInsertReportExceptionRequest;
    }

    /**
     * 校验上传的报备信息是否符合要求
     * @return
     */
    private String checkTemplateCompliance(UploadReportExceptionInfoRequest request, String categoryId) {
        List<ExceptionReportQuestionItem> exceptionReportQuestionItemList = LionConfigUtil.queryExceptionReportQuestionItemList();

        boolean isCheck = false;
        for (ExceptionReportQuestionItem item : exceptionReportQuestionItemList) {
            if (!String.valueOf(item.getCategoryId()).equals(categoryId)){
                continue;
            }
            if (item.getId() != request.getId()) {
                continue;
            }
            isCheck = true;
            int maxPictureNum = item.getMaxPictureNum();
            boolean needPicture = item.isNeedPicture();
            boolean needReason = item.isNeedReason();
            if (needPicture && CollectionUtils.isEmpty(request.getMediaUrlList())) {
                return "请上传图片/视频";
            }
            if (needPicture && CollectionUtils.size(request.getMediaUrlList()) > maxPictureNum) {
                return "图片/视频数量超过限制";
            }
            // 无图片/视频数量限制的问题，最多5张
            if (!needPicture && CollectionUtils.size(request.getMediaUrlList()) > 5){
                return "图片/视频数量超过限制";
            }
            if (needReason && StringUtils.isEmpty(request.getReason())) {
                return "请输入具体原因";
            }
            break;
        }
        if (!isCheck){
            return "报备问题id错误";
        }
        return null;
    }

    private String checkTemplateComplianceNew(UploadReportExceptionInfoRequest request, String categoryId) {
        List<ExceptionReportQuestionItem> exceptionReportQuestionItemList = LionConfigUtil.queryExceptionReportQuestionItemListNew();

        boolean isCheck = false;
        for (ExceptionReportQuestionItem item : exceptionReportQuestionItemList) {
            List<ExceptionReportQuestionItem>children = item.getChildren();
            for (ExceptionReportQuestionItem child : children) {
                if (!String.valueOf(child.getCategoryId()).equals(categoryId)){
                    continue;
                }
                if (child.getId() != request.getId()) {
                    continue;
                }
                isCheck = true;
                int maxPictureNum = child.getMaxPictureNum();
                boolean needPicture = child.isNeedPicture();
                boolean needReason = child.isNeedReason();
                if (needPicture && CollectionUtils.isEmpty(request.getMediaUrlList())) {
                    return "请上传图片/视频";
                }
                if (needPicture && CollectionUtils.size(request.getMediaUrlList()) > maxPictureNum) {
                    return "图片/视频数量超过限制";
                }
                // 无图片/视频数量限制的问题，最多5张
                if (!needPicture && CollectionUtils.size(request.getMediaUrlList()) > 5){
                    return "图片/视频数量超过限制";
                }
                if (needReason && StringUtils.isEmpty(request.getReason())) {
                    return "请输入具体原因";
                }
                break;
            }
        }
        if (!isCheck){
            return "报备问题id错误";
        }
        return null;
    }

    private DoorToDoorTakeOrderRecordDTO getDoorToDoorTakeOrderRecordDTO(UploadReportExceptionInfoRequest request) {
        DoorToDoorTakeOrderRecordDTO doorToDoorTakeOrderRecordDTO = null;
        try{
            TechnicianResp<DoorToDoorTakeOrderRecordDTO> technicianResp = doorToDoorTakeOrderRecordService.queryTakeOrderByTechIdAndOrderId(request.getTechId() , request.getServiceOrderId());
            if (technicianResp != null && technicianResp.respSuccess() && technicianResp.getData() != null) {
                doorToDoorTakeOrderRecordDTO = technicianResp.getData();
            }
        } catch (Exception e) {
            log.error("uploadReportExceptionInfo queryTakeOrderByTechIdAndOrderId error， techId={}, serviceOrderId={}", request.getTechId(), request.getServiceOrderId(), e);
        }
        return doorToDoorTakeOrderRecordDTO;
    }


    private TechnicianResp<ReportExceptionTemplateResponse> buildReportExceptionTemplateResponse(String categoryId, List<ExceptionReportQuestionItem> exceptionReportQuestionItemList) {
        ReportExceptionTemplateResponse response = new ReportExceptionTemplateResponse();
        response.setTitle("日常报备");
        buildReportIssueModule(response, categoryId, exceptionReportQuestionItemList);
        buildEvidencePictureModule(response);
        buildIssueReasonModule(response);
        response.setReportType(ReportTypeEnum.DAILY_REPORTING.getType());
        log.info("日常报备 :{}", response);
        return TechnicianResp.success(response);
    }

    private void buildReportIssueModule(ReportExceptionTemplateResponse response, String categoryId, List<ExceptionReportQuestionItem> questionItems) {
        ReportIssueModule reportIssueModule = new ReportIssueModule();
        reportIssueModule.setTitle("请选择您需要报备的问题（单选）");

        // 过滤出当前类目的一级问题
        List<ReportIssue> reportIssueList = questionItems.stream()
                .filter(item -> String.valueOf(item.getCategoryId()).equals(categoryId))
                .map(item -> {
                    ReportIssue reportIssue = convertToReportIssue(item);
                    // 处理子问题
                    if (CollectionUtils.isNotEmpty(item.getChildren())) {
                        reportIssue.setHasChildren(true);
                        reportIssue.setChildren(item.getChildren().stream()
                                .map(this::convertToReportIssue)
                                .collect(Collectors.toList()));
                    }
                    return reportIssue;
                })
                .collect(Collectors.toList());
        reportIssueModule.setReportIssueList(reportIssueList);
        response.setReportIssueModule(reportIssueModule);
    }

    private ReportIssue convertToReportIssue(ExceptionReportQuestionItem item) {
        ReportIssue reportIssue = new ReportIssue();
        reportIssue.setId(item.getId());
        reportIssue.setText(item.getText());
        reportIssue.setNeedMedia(item.isNeedPicture());
        reportIssue.setNeedReason(item.isNeedReason());
        reportIssue.setMaxUploadPicNum(item.getMaxPictureNum());
        return reportIssue;
    }

    private void buildEvidencePictureModule(ReportExceptionTemplateResponse response) {
        EvidencePictureModule evidencePictureModule = new EvidencePictureModule();
        evidencePictureModule.setTitle("请上传举证图片/视频");
        evidencePictureModule.setNote("请拍摄能证明以上问题的图片/视频（1-5张）");
        response.setEvidencePictures(evidencePictureModule);
    }

    private void buildIssueReasonModule(ReportExceptionTemplateResponse response) {
        IssueReasonModule issueReasonModule = new IssueReasonModule();
        issueReasonModule.setTitle("请描述您的具体情况");
        issueReasonModule.setNote("输入具体原因，不超过100字");
        issueReasonModule.setMaxNoteSize(100);
        response.setIssueReason(issueReasonModule);
    }

    private String getCategoryId(long serviceOrderId){
        if (serviceOrderId <= 0) {
            return null;
        }
        TechnicianResp<TechServiceOrderDTO> serviceOrderResp = null;
        try {
            serviceOrderResp = doorToDoorTechServiceOrderQueryService.findDetailByOrderId(serviceOrderId);
        } catch (Exception e) {
            log.error("reportExceptionTemplate query serviceOrder error! serviceOrderId={}", serviceOrderId, e);
        }
        if (serviceOrderResp == null || serviceOrderResp.getData() == null || StringUtils.isEmpty(serviceOrderResp.getData().getExtraInfo())) {
            return null;
        }
        return DoorToDoorUtils.getServiceOrderCategory(serviceOrderResp.getData());
    }

    private String checkParamAndReturnCategoryId(){
        long serviceOrderId = getLongParameter("serviceOrderId");
        if (serviceOrderId <= 0) {
            throw new BizException("入参错误") ;
        }
        // 查类目信息
        String categoryId = getCategoryId(serviceOrderId);
        if (StringUtils.isEmpty(categoryId)) {
            throw new BizException("获取类目失败") ;
        }
        // 类目是否支持异常报备
        if (!LionConfigUtil.checkCategorySupportReportException(categoryId)) {
            throw new BizException("当前类目不支持异常报备") ;
        }
        return categoryId;
    }


}