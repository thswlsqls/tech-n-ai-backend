package com.tech.n.ai.domain.aurora.generator;

import com.github.f4b6a3.tsid.Tsid;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

/**
 * TSID 생성기
 */
public class TsidGenerator implements IdentifierGenerator {
    
    /**
     * TSID 값을 생성하여 반환
     */
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        return Tsid.fast().toLong();
    }
}
