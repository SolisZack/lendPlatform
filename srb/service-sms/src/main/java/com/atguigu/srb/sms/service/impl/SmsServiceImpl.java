package com.atguigu.srb.sms.service.impl;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.atguigu.common.exception.Assert;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.sms.service.SmsService;
import com.atguigu.srb.sms.util.SmsProperties;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class SmsServiceImpl implements SmsService {
    @Override
    public void send(String mobile, String templateCode, Map<String, Object> param) {

        //创建远程连接客户端对象
        DefaultProfile profile = DefaultProfile.getProfile(
                SmsProperties.REGION_Id,
                SmsProperties.KEY_ID,
                SmsProperties.KEY_SECRET);
        IAcsClient client = new DefaultAcsClient(profile);

        //创建远程连接的请求参数
        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain("dysmsapi.aliyuncs.com");
        request.setSysVersion("2017-05-25");
        request.setSysAction("SendSms");
        request.putQueryParameter("RegionId", SmsProperties.REGION_Id);
        request.putQueryParameter("PhoneNumbers", mobile);
        request.putQueryParameter("SignName", SmsProperties.SIGN_NAME);
        request.putQueryParameter("TemplateCode", templateCode);

        Gson gson = new Gson();
        String jsonParam = gson.toJson(param);
        request.putQueryParameter("TemplateParam", jsonParam);
        try {
            //使用客户端对象携带请求参数向远程阿里云服务器发起远程调用，并得到响应结果
            CommonResponse response = client.getCommonResponse(request);
            System.out.println("response.getData()：" + response.getData());

            //通信失败的处理
            boolean success = response.getHttpResponse().isSuccess();
            Assert.isTrue(success, ResponseEnum.ALIYUN_RESPONSE_ERROR);

            //获取响应结果
            String data = response.getData();
            HashMap<String, String> resultMap = gson.fromJson(data, HashMap.class);
            String code = resultMap.get("Code");
            String message = resultMap.get("Message");
            log.info("code：" + code + "，message：" + message);

            //业务失败的处理
            Assert.notEquals("isv.BUSINESS_LIMIT_CONTROL", code, ResponseEnum.ALIYUN_SMS_LIMIT_CONTROL_ERROR);
            Assert.equals("OK", code, ResponseEnum.ALIYUN_SMS_ERROR);

        } catch (ServerException e) {
            log.error("阿里云短信发送sdk调用失败:" + e.getErrCode() + ", " + e.getErrMsg());
            throw new BusinessException(ResponseEnum.ALIYUN_SMS_ERROR, e);
//            e.printStackTrace();
        } catch (ClientException e) {
            log.error("阿里云短信发送sdk调用失败:" + e.getErrCode() + ", " + e.getErrMsg());
            throw new BusinessException(ResponseEnum.ALIYUN_SMS_ERROR, e);
//            e.printStackTrace();
        }
    }

    public static com.aliyun.dysmsapi20170525.Client createClient() throws Exception {
        Config config = new Config()
                // 您的 AccessKey ID
                .setAccessKeyId(SmsProperties.KEY_ID)
                // 您的 AccessKey Secret
                .setAccessKeySecret(SmsProperties.KEY_SECRET);
        // 访问的域名
        config.endpoint = "dysmsapi.aliyuncs.com";
        return new com.aliyun.dysmsapi20170525.Client(config);
    }

    @Override
    public void newSend(String phoneNumber, String SmsCode) {
        try {
            // create Client
            com.aliyun.dysmsapi20170525.Client client = createClient();
            // create Sms Request
            SendSmsRequest sendSmsRequest = new SendSmsRequest()
                    .setSignName("阿里云短信测试")
                    .setTemplateCode("SMS_154950909")
                    .setPhoneNumbers(phoneNumber)
                    .setTemplateParam(String.format("{\"code\":\"%s\"}", SmsCode));  // "{\"code\":\"1234\"}"
            RuntimeOptions runtime = new RuntimeOptions();
            // Send Sms
            SendSmsResponse sendSmsResponse = client.sendSmsWithOptions(sendSmsRequest, runtime);
            // Check if get response from ALIYUN
            Integer statusCode = sendSmsResponse.getStatusCode();
            if (statusCode < 200 || statusCode >= 300)
                throw new BusinessException(ResponseEnum.ALIYUN_RESPONSE_ERROR);
            // Check if successfully send sms
            log.info("阿里云短信发送结果:" + sendSmsResponse.getBody().getCode() + ", " + sendSmsResponse.getBody().getMessage());
            Assert.equals("OK", sendSmsResponse.getBody().getCode(), ResponseEnum.ALIYUN_SMS_ERROR);
        } catch (TeaException error) {
            // Throw and print error
            log.error("阿里云短信发送sdk调用失败:");
            throw new BusinessException(ResponseEnum.ALIYUN_SMS_ERROR, error);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // Throw and print error
            log.error("阿里云短信发送sdk调用失败:");
            throw new BusinessException(ResponseEnum.ALIYUN_SMS_ERROR, error);
        }
    }


}
