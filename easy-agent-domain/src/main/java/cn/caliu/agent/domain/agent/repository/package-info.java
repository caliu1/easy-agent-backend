/**
 * 领域仓储接口包（Repository Port）。
 *
 * 职责：
 * 1. 定义领域读写所需的持久化能力抽象。
 * 2. 不关心具体数据库与 ORM 技术细节。
 * 3. 由 Infrastructure 层实现具体仓储适配器。
 */
package cn.caliu.agent.domain.agent.repository;
