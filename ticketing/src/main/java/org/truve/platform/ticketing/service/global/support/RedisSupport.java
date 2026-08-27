package org.truve.platform.ticketing.service.global.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisSupport {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;


	public void setValue(String key, String value) {
		redisTemplate.opsForValue().set(key, value);
	}

	public void setValueWithTtl(String key, String value, Duration duration) {
		redisTemplate.opsForValue().set(key, value, duration);
	}

	public String getValue(String key) {
		return redisTemplate.opsForValue().get(key);
	}

	public boolean delete(String key) {
		return Boolean.TRUE.equals(redisTemplate.delete(key));
	}

	public boolean consumeIfEquals(String key, String expectedValue) {
		String script =
			"local current = redis.call('GET', KEYS[1]) " +
				"if (not current) then return 0 end " +
				"if (current == ARGV[1]) then " +
				"redis.call('DEL', KEYS[1]) " +
				"return 1 " +
				"end " +
				"return 0";

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			Collections.singletonList(key),
			expectedValue
		);
		return Long.valueOf(1L).equals(result);
	}


	public void setJsonValue(String key, Object value) {
		try {
			String json = objectMapper.writeValueAsString(value);
			setValue(key, json);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.JSON_PARSE_ERROR);
		}
	}

	public void setJsonValueWithTtl(String key, Object value, Duration duration) {
		try {
			String json = objectMapper.writeValueAsString(value);
			setValueWithTtl(key, json, duration);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.JSON_PARSE_ERROR);
		}
	}

	public <T> T getJsonValue(String key, Class<T> type) {
		String json = getValue(key);
		if (json == null) return null;
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException e) {
			throw new CustomException(ErrorCode.JSON_PARSE_ERROR);
		}
	}

	public boolean setIfAbsent(String key, String value, Duration duration) {
		return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, duration));
	}

	public long holdSeatsWithLimit(
		List<String> keys,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String seatKeyPrefix,
		String protectedValuePrefix,
		Duration seatTtl,
		Duration sessionSetTtl,
		int maxSeatCount
	) {
		String script = """
			local sessionSetKey = KEYS[1]
			local sessionToken = ARGV[1]
			local seatTtlMillis = ARGV[2]
			local sessionSetTtlMillis = ARGV[3]
			local maxSeatCount = tonumber(ARGV[4])
			local seatKeyPrefix = ARGV[5]
			local protectedValuePrefix = ARGV[6]

			local members = redis.call('SMEMBERS', sessionSetKey)
			for _, scheduledSeatId in ipairs(members) do
				local owner = redis.call('GET', seatKeyPrefix .. scheduledSeatId)
				if not owner or (owner ~= sessionToken and string.sub(owner, 1, string.len(protectedValuePrefix)) ~= protectedValuePrefix) then
					redis.call('SREM', sessionSetKey, scheduledSeatId)
				end
			end

			local additionalSeatCount = 0
			for i = 2, #KEYS do
				local current = redis.call('GET', KEYS[i])
				if current and current ~= sessionToken then
					return 0
				end
				local scheduledSeatId = ARGV[i + 5]
				if redis.call('SISMEMBER', sessionSetKey, scheduledSeatId) == 0 then
					additionalSeatCount = additionalSeatCount + 1
				end
			end

			if redis.call('SCARD', sessionSetKey) + additionalSeatCount > maxSeatCount then
				return -1
			end

			for i = 2, #KEYS do
				local current = redis.call('GET', KEYS[i])
				if not current then
					redis.call('SET', KEYS[i], sessionToken, 'PX', seatTtlMillis, 'NX')
				end
				redis.call('SADD', sessionSetKey, ARGV[i + 5])
			end
			redis.call('PEXPIRE', sessionSetKey, sessionSetTtlMillis)
			return 1
			""";

		List<String> arguments = new ArrayList<>();
		arguments.add(sessionToken);
		arguments.add(String.valueOf(seatTtl.toMillis()));
		arguments.add(String.valueOf(sessionSetTtl.toMillis()));
		arguments.add(String.valueOf(maxSeatCount));
		arguments.add(seatKeyPrefix);
		arguments.add(protectedValuePrefix);
		arguments.addAll(scheduledSeatIds.stream().map(String::valueOf).toList());

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			keys,
			arguments.toArray()
		);
		return result == null ? 0L : result;
	}

	public long holdSeatLeasesWithLimit(
		List<String> keys,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String holdId,
		String seatKeyPrefix,
		String holdMetaKeyPrefix,
		Duration seatTtl,
		Duration sessionSetTtl,
		int maxSeatCount
	) {
		String script = """
			local sessionSetKey = KEYS[1]
			local holdMetaKey = KEYS[2]
			local sessionToken = ARGV[1]
			local holdId = ARGV[2]
			local seatTtlMillis = ARGV[3]
			local sessionSetTtlMillis = ARGV[4]
			local maxSeatCount = tonumber(ARGV[5])
			local seatKeyPrefix = ARGV[6]
			local holdMetaKeyPrefix = ARGV[7]

			local members = redis.call('SMEMBERS', sessionSetKey)
			for _, scheduledSeatId in ipairs(members) do
				local ownerHoldId = redis.call('GET', seatKeyPrefix .. scheduledSeatId)
				local ownerSessionToken = false
				if ownerHoldId then
					ownerSessionToken = redis.call('GET', holdMetaKeyPrefix .. ownerHoldId)
				end
				if not ownerHoldId or ownerSessionToken ~= sessionToken then
					redis.call('SREM', sessionSetKey, scheduledSeatId)
				end
			end

			local sameOwnerCount = 0
			local missingCount = 0
			for i = 3, #KEYS do
				local current = redis.call('GET', KEYS[i])
				if current == holdId then
					sameOwnerCount = sameOwnerCount + 1
				elseif current then
					return 0
				else
					missingCount = missingCount + 1
				end
			end

			local holdSessionToken = redis.call('GET', holdMetaKey)
			if sameOwnerCount > 0 and missingCount > 0 then
				return 0
			end
			if sameOwnerCount > 0 then
				if holdSessionToken == sessionToken then
					return 2
				end
				return 0
			end
			if holdSessionToken then
				return 0
			end

			if redis.call('SCARD', sessionSetKey) + missingCount > maxSeatCount then
				return -1
			end

			for i = 3, #KEYS do
				redis.call('SET', KEYS[i], holdId, 'PX', seatTtlMillis, 'NX')
				redis.call('SADD', sessionSetKey, ARGV[i + 5])
			end
			redis.call('SET', holdMetaKey, sessionToken, 'PX', seatTtlMillis, 'NX')
			redis.call('PEXPIRE', sessionSetKey, sessionSetTtlMillis)
			return 1
			""";

		List<String> arguments = new ArrayList<>();
		arguments.add(sessionToken);
		arguments.add(holdId);
		arguments.add(String.valueOf(seatTtl.toMillis()));
		arguments.add(String.valueOf(sessionSetTtl.toMillis()));
		arguments.add(String.valueOf(maxSeatCount));
		arguments.add(seatKeyPrefix);
		arguments.add(holdMetaKeyPrefix);
		arguments.addAll(scheduledSeatIds.stream().map(String::valueOf).toList());

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			keys,
			arguments.toArray()
		);
		return result == null ? 0L : result;
	}

	public boolean compensateNewlyHeldSeatLeases(
		List<String> keys,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String holdId
	) {
		String script = """
			if redis.call('GET', KEYS[2]) ~= ARGV[1] then
				return 0
			end

			local deleted = 0
			for i = 3, #KEYS do
				if redis.call('GET', KEYS[i]) == ARGV[2] then
					redis.call('DEL', KEYS[i])
					redis.call('SREM', KEYS[1], ARGV[i])
					deleted = deleted + 1
				end
			end
			redis.call('DEL', KEYS[2])
			if redis.call('SCARD', KEYS[1]) == 0 then
				redis.call('DEL', KEYS[1])
			end
			return deleted
			""";

		List<String> arguments = new ArrayList<>();
		arguments.add(sessionToken);
		arguments.add(holdId);
		arguments.addAll(scheduledSeatIds.stream().map(String::valueOf).toList());

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			keys,
			arguments.toArray()
		);
		return result != null && result > 0;
	}

	public boolean releaseHeldSeats(
		List<String> keys,
		List<Long> scheduledSeatIds,
		String sessionToken
	) {
		String script = """
			for i = 2, #KEYS do
				if redis.call('GET', KEYS[i]) ~= ARGV[1] then
					return 0
				end
			end
			for i = 2, #KEYS do
				redis.call('DEL', KEYS[i])
				redis.call('SREM', KEYS[1], ARGV[i])
			end
			if redis.call('SCARD', KEYS[1]) == 0 then
				redis.call('DEL', KEYS[1])
			end
			return 1
			""";

		List<String> arguments = new ArrayList<>();
		arguments.add(sessionToken);
		arguments.addAll(scheduledSeatIds.stream().map(String::valueOf).toList());

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			keys,
			arguments.toArray()
		);
		return Long.valueOf(1L).equals(result);
	}

	public long releaseSessionHeldSeats(String sessionSetKey, String seatKeyPrefix, String sessionToken) {
		String script = """
			local deleted = 0
			local members = redis.call('SMEMBERS', KEYS[1])
			for _, scheduledSeatId in ipairs(members) do
				local seatKey = ARGV[2] .. scheduledSeatId
				if redis.call('GET', seatKey) == ARGV[1] then
					redis.call('DEL', seatKey)
					deleted = deleted + 1
				end
			end
			redis.call('DEL', KEYS[1])
			return deleted
			""";

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			Collections.singletonList(sessionSetKey),
			sessionToken,
			seatKeyPrefix
		);
		return result == null ? 0L : result;
	}

	public boolean claimHeldSeats(
		List<String> keys,
		List<Long> scheduledSeatIds,
		String sessionToken,
		String claimValue
	) {
		String script = """
			for i = 2, #KEYS do
				if redis.call('GET', KEYS[i]) ~= ARGV[1]
					or redis.call('SISMEMBER', KEYS[1], ARGV[i + 1]) == 0 then
					return 0
				end
			end
			for i = 2, #KEYS do
				redis.call('SET', KEYS[i], ARGV[2], 'KEEPTTL')
			end
			return 1
			""";

		List<String> arguments = new ArrayList<>();
		arguments.add(sessionToken);
		arguments.add(claimValue);
		arguments.addAll(scheduledSeatIds.stream().map(String::valueOf).toList());

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			keys,
			arguments.toArray()
		);
		return Long.valueOf(1L).equals(result);
	}

	public boolean releaseClaimedSeats(
		List<String> keys,
		List<Long> scheduledSeatIds,
		String claimValue
	) {
		String script = """
			local deleted = 0
			for i = 2, #KEYS do
				local current = redis.call('GET', KEYS[i])
				if current == ARGV[1] then
					redis.call('DEL', KEYS[i])
					redis.call('SREM', KEYS[1], ARGV[i])
					deleted = deleted + 1
				elseif not current then
					redis.call('SREM', KEYS[1], ARGV[i])
				end
			end
			if redis.call('SCARD', KEYS[1]) == 0 then
				redis.call('DEL', KEYS[1])
			end
			return deleted
			""";

		List<String> arguments = new ArrayList<>();
		arguments.add(claimValue);
		arguments.addAll(scheduledSeatIds.stream().map(String::valueOf).toList());

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			keys,
			arguments.toArray()
		);
		return result != null && result > 0;
	}

	public boolean restoreClaimedSeats(
		List<String> keys,
		List<Long> scheduledSeatIds,
		String claimValue,
		String sessionToken,
		Duration sessionSetTtl
	) {
		String script = """
			local restored = 0
			for i = 2, #KEYS do
				if redis.call('GET', KEYS[i]) == ARGV[1] then
					redis.call('SET', KEYS[i], ARGV[2], 'KEEPTTL')
					redis.call('SADD', KEYS[1], ARGV[i + 2])
					restored = restored + 1
				end
			end
			if restored > 0 then
				redis.call('PEXPIRE', KEYS[1], ARGV[3])
			end
			return restored
			""";

		List<String> arguments = new ArrayList<>();
		arguments.add(claimValue);
		arguments.add(sessionToken);
		arguments.add(String.valueOf(sessionSetTtl.toMillis()));
		arguments.addAll(scheduledSeatIds.stream().map(String::valueOf).toList());

		Long result = redisTemplate.execute(
			new DefaultRedisScript<>(script, Long.class),
			keys,
			arguments.toArray()
		);
		return result != null && result > 0;
	}

	public void zAdd(String key, String member, double score) {
		redisTemplate.opsForZSet().add(key, member, score);
	}

	public long zRemRangeByScore(String key, double minScore, double maxScore) {
		return redisTemplate.opsForZSet().removeRangeByScore(key, minScore, maxScore);
	}

	public void zRem(String key, String member) {
		redisTemplate.opsForZSet().remove(key, member);
	}

	public boolean expireSeconds(String key, long ttl) {
		return Boolean.TRUE.equals(redisTemplate.expire(key, ttl, TimeUnit.SECONDS));
	}

	public long getTtlMillis(String key) {
		return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
	}
}
