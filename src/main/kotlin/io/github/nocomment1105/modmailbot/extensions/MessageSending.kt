package io.github.nocomment1105.modmailbot.extensions

import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.noGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.utils.createdAt
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.create.embed
import io.github.nocomment1105.modmailbot.config
import io.github.nocomment1105.modmailbot.database.DatabaseManager
import io.github.nocomment1105.modmailbot.database.getOpenThreadsForUser
import io.github.nocomment1105.modmailbot.messageEmbed
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import mu.KotlinLogging
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

// TODO Come up with a good class name wtf
class MessageSending : Extension() {

	override val name = "main"

	override suspend fun setup() {
		val logger = KotlinLogging.logger("Thread Controls")

		event<MessageCreateEvent> {
			check {
				noGuild()
				failIf(event.message.author!!.id == kord.selfId)
			}
			action {
				/*
				TODO Implement a check to see if the user already has an open channel, if they do, direct the message
				 toward that instead.
				 */
				var openThread = false
				newSuspendedTransaction {
					openThread = try {
						DatabaseManager.OpenThreads.select {
							DatabaseManager.OpenThreads.userId eq event.message.author?.id.toString()
						}.single()
						true
					} catch (e: NoSuchElementException) {
						logger.info("No Thread found! Creating thread for ${event.message.author?.id}")
						false
					}
				}

				val mailChannel: TextChannel

				if (!openThread) {
					mailChannel = kord.getGuild(
						Snowflake(config.getProperty("mail_server_id"))
					)!!.createTextChannel(event.message.author!!.tag)
					newSuspendedTransaction {
						DatabaseManager.OpenThreads.insertIgnore {
							it[userId] = event.message.author?.id.toString()
							it[threadId] = mailChannel.id.toString()
						}
					}

					mailChannel.createMessage {
						content = "@here" // TODO Implement a config options system
						embed {
							description = "${event.message.author!!.mention} was created " +
									event.message.author!!.fetchUser().createdAt.toDiscord(TimestampType.LongDateTime)
							timestamp = Clock.System.now()
							color = DISCORD_BLURPLE

							field {
								name = "Nickname"
								value = event.message.author!!.asMember(
									Snowflake(config.getProperty("main_server_id"))
								).nickname.toString()
								inline = true
							}
							field {
								val roles = event.message.author!!.asMember(
									Snowflake(config.getProperty("main_server_id"))
								).roles.toList().map { it }
								name = "Roles"
								roles.forEach {
									value += "${it.name}\n"
								}
								inline = true
							}

							author {
								name = event.message.author?.tag
								icon = event.message.author?.avatar!!.url
							}

							footer {
								text = "User ID: ${event.message.author!!.id} |" +
										" DM ID: ${event.message.author!!.getDmChannel().id}"
							}
						}

						messageEmbed(event.message)
					}
				} else {
					val mailChannelId: String

					try {
						mailChannelId = getOpenThreadsForUser(event.message.author!!.id, DatabaseManager.OpenThreads.threadId)
					} catch (e: NoSuchElementException) {
						logger.error("User passed checks yet has no data? What?")
						return@action
					}

					mailChannel = kord.getGuild(
						Snowflake(config.getProperty("mail_server_id"))
					)!!.getChannelOf(Snowflake(mailChannelId))

					mailChannel.createMessage {
						messageEmbed(event.message)
					}
				}
			}
		}

		event<MessageCreateEvent> {
			check {
				inGuild(Snowflake(config.getProperty("mail_server_id")))
			}
			action {
				/*
				TODO Use a database when creating channels in the inbox server, allowing me to grab the user Id from
				 from the previous message create event.
				 */
			}
		}
	}
}