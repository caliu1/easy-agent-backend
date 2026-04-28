/**
 * 领域服务包（Domain Service）。
 *
 * 职责：
 * 1. 承载跨实体、跨聚合的业务编排逻辑。
 * 2. 调用仓储端口完成状态变更与查询。
 * 3. 输出领域对象，不泄露基础设施细节。
 */
package cn.caliu.agent.domain.agent.service;
