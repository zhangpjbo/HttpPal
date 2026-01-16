package com.httppal.service

import net.datafaker.Faker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 智能字段名识别器
 * 根据字段名称自动选择合适的mock数据，提升数据真实感
 */
class SmartFieldNameProvider(private val faker: Faker) {

    private val random = Random.Default

    private val fieldMappings = mapOf(
        // 用户相关
        "username" to { faker.credentials().username() },
        "name" to { faker.name().fullName() },
        "fullname" to { faker.name().fullName() },
        "firstname" to { faker.name().firstName() },
        "lastname" to { faker.name().lastName() },
        "nickname" to { faker.name().nameWithMiddle() },
        "realname" to { faker.name().fullName() },

        // 联系方式
        "email" to { faker.internet().emailAddress() },
        "mail" to { faker.internet().emailAddress() },
        "phone" to { faker.phoneNumber().phoneNumber() },
        "mobile" to { faker.phoneNumber().cellPhone() },
        "telephone" to { faker.phoneNumber().phoneNumber() },
        "tel" to { faker.phoneNumber().phoneNumber() },
        "cellphone" to { faker.phoneNumber().cellPhone() },

        // 地址相关
        "address" to { faker.address().fullAddress() },
        "street" to { faker.address().streetAddress() },
        "city" to { faker.address().city() },
        "province" to { faker.address().state() },
        "state" to { faker.address().state() },
        "country" to { faker.address().country() },
        "zipcode" to { faker.address().zipCode() },
        "postcode" to { faker.address().zipCode() },
        "zip" to { faker.address().zipCode() },

        // 公司相关
        "company" to { faker.company().name() },
        "companyname" to { faker.company().name() },
        "organization" to { faker.company().name() },
        "department" to { faker.company().industry() },
        "jobtitle" to { faker.job().title() },
        "position" to { faker.job().position() },
        "title" to { faker.job().title() },

        // 网络相关
        "url" to { faker.internet().url() },
        "website" to { faker.internet().url() },
        "domain" to { faker.internet().domainName() },
        "ip" to { faker.internet().ipV4Address() },
        "ipaddress" to { faker.internet().ipV4Address() },
        "ipv4" to { faker.internet().ipV4Address() },
        "ipv6" to { faker.internet().ipV6Address() },
        "mac" to { faker.internet().macAddress() },
        "macaddress" to { faker.internet().macAddress() },

        // 身份相关
        "idcard" to { generateChineseIDCard() },
        "idnumber" to { generateChineseIDCard() },
        "passport" to { faker.passport().valid() },
        "ssn" to { faker.idNumber().valid() },

        // 金融相关
        "price" to { faker.commerce().price() },
        "amount" to { faker.number().randomDouble(2, 1, 10000).toString() },
        "money" to { faker.commerce().price() },
        "bankcard" to { faker.finance().creditCard() },
        "creditcard" to { faker.finance().creditCard() },
        "card" to { faker.finance().creditCard() },
        "cardnumber" to { faker.finance().creditCard() },

        // 内容相关
        "booktitle" to { faker.book().title() },
        "description" to { faker.lorem().paragraph() },
        "content" to { faker.lorem().paragraph(3) },
        "summary" to { faker.lorem().sentence(10) },
        "comment" to { faker.lorem().sentence() },
        "message" to { faker.lorem().sentence() },
        "text" to { faker.lorem().paragraph() },
        "remark" to { faker.lorem().sentence() },

        // 日期时间
        "createtime" to { LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) },
        "updatetime" to { LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) },
        "createdat" to { LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) },
        "updatedat" to { LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) },
        "birthday" to { generateRandomBirthday() },
        "birthdate" to { generateRandomBirthday() },
        "date" to { LocalDate.now().format(DateTimeFormatter.ISO_DATE) },
        "time" to { LocalDateTime.now().format(DateTimeFormatter.ISO_TIME) },
        "datetime" to { LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) },

        // 商品相关
        "productname" to { faker.commerce().productName() },
        "product" to { faker.commerce().productName() },
        "brand" to { faker.company().name() },
        "category" to { faker.commerce().department() },
        "color" to { faker.color().name() },
        "material" to { faker.commerce().material() },

        // 其他
        "avatar" to { faker.avatar().image() },
        "image" to { faker.internet().image() },
        "imageurl" to { faker.internet().image() },
        "photo" to { faker.internet().image() },
        "code" to { faker.code().isbn13() },
        "isbn" to { faker.code().isbn13() },
        "uuid" to { faker.internet().uuid() },
        "guid" to { faker.internet().uuid() },
        "id" to { faker.number().numberBetween(1, 100000).toString() },
        "status" to { listOf("active", "inactive", "pending", "completed").random() },
        "gender" to { listOf("male", "female").random() },
        "sex" to { listOf("male", "female").random() },
        "password" to { faker.credentials().password() },
        "token" to { faker.internet().uuid() },
        "accesstoken" to { faker.internet().uuid() },

        // 数字相关
        "age" to { faker.number().numberBetween(18, 65).toString() },
        "count" to { faker.number().numberBetween(0, 1000).toString() },
        "total" to { faker.number().numberBetween(0, 10000).toString() },
        "score" to { faker.number().numberBetween(0, 100).toString() },
        "rating" to { faker.number().randomDouble(1, 0, 5).toString() }
    )

    /**
     * 根据字段名生成值
     * @param fieldName 字段名称
     * @param dataType 数据类型（可选）
     * @return 生成的值，null表示无法识别，应使用默认策略
     */
    fun generateByFieldName(fieldName: String, dataType: String?): String? {
        if (fieldName.isBlank()) return null

        val normalizedName = normalizeFieldName(fieldName)

        // 精确匹配
        fieldMappings[normalizedName]?.let { return it() }

        // 模糊匹配（包含关键词）
        for ((key, generator) in fieldMappings) {
            if (normalizedName.contains(key)) {
                return generator()
            }
        }

        return null
    }

    /**
     * 标准化字段名称
     * 移除下划线、连字符，转换为小写
     */
    private fun normalizeFieldName(fieldName: String): String {
        return fieldName
            .lowercase()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
    }

    /**
     * 生成中国身份证号（18位）
     * 格式：6位地区码 + 8位生日 + 3位顺序码 + 1位校验码
     */
    private fun generateChineseIDCard(): String {
        // 常见地区码
        val areaCodes = listOf(
            "110000", // 北京
            "310000", // 上海
            "440100", // 广州
            "440300", // 深圳
            "500000", // 重庆
            "330100", // 杭州
            "320100", // 南京
            "610100"  // 西安
        )
        val areaCode = areaCodes.random()

        // 生成生日（18-60岁）
        val birthDate = LocalDate.now().minusYears(random.nextLong(18, 61))
        val dateStr = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        // 顺序码（100-999）
        val sequence = random.nextInt(100, 1000).toString()

        val base17 = areaCode + dateStr + sequence

        // 计算校验码
        val weights = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
        val checkCodes = charArrayOf('1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2')
        var sum = 0
        for (i in 0..16) {
            sum += (base17[i] - '0') * weights[i]
        }
        val checkCode = checkCodes[sum % 11]

        return base17 + checkCode
    }

    /**
     * 生成随机生日（18-60岁之间）
     */
    private fun generateRandomBirthday(): String {
        val birthDate = LocalDate.now().minusYears(random.nextLong(18, 61))
        return birthDate.format(DateTimeFormatter.ISO_DATE)
    }
}
