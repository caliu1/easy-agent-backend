/**
 * 仓储实现包（Repository Adapter）。
 *
 * 职责：
 * 1. 实现 Domain 层仓储接口。
 * 2. 负责 Entity/VO 与 PO/DAO 之间的转换。
 * 3. 保持“业务语义在 Domain、技术细节在 Infrastructure”的边界。
 */
package cn.caliu.agent.infrastructure.persistent.repository;
