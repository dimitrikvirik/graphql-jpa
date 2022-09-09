package com.example.demo.config

import com.example.demo.domain.Employee
import com.github.ichanzhar.rsql.JpaRsqlVisitor
import com.github.ichanzhar.rsql.utils.RsqlParserFactory
import com.github.wnameless.json.unflattener.JsonUnflattener
import graphql.language.EnumValue
import graphql.language.ListType
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.SelectedField
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.domain.Specification
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import javax.persistence.EntityManagerFactory
import javax.persistence.criteria.*
import javax.servlet.http.HttpServletRequest


data class GraphQlProperty(
    val name: String,
    var alias: String = "",
    val parent: GraphQlProperty? = null,
    val isEmbedded: Boolean,
    val isList: Boolean,
    val joinType: JoinType? = null,
    var path: Path<*>? = null,
)


@Configuration
class GraphqlConfig {


    @Bean
    fun runtimeWiringConfigurer(emf: EntityManagerFactory, request: HttpServletRequest): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { builder ->
            builder.type("Query") { type ->
                type.dataFetcher("employees") {
                    graphqlQueryToResult(it.arguments["search"] as String, it, emf, Employee::class.java)
                }
            }
        }


    private fun graphqlQueryToResult(
        search: String,
        env: DataFetchingEnvironment,
        emf: EntityManagerFactory,
        entity: Class<*>,
    ): MutableList<Any?> {
        val qlProperties =
            env.selectionSet.asProperty()
        val entityManager = emf.createEntityManager()
        val cb = entityManager.criteriaBuilder
        val cq = cb.createTupleQuery()
        val rt = cq.from(entity) as Root<Any>

        val selections = mutableListOf<Selection<Any>>()
        for (i in qlProperties.indices) {
            val property = qlProperties[i]
            if (property.joinType != null) {

                val join =
                    ((property.parent?.path ?: rt) as From<*, *>).join<Any, Any>(property.name, property.joinType)
                property.path = join

            } else if (property.isEmbedded) {
                val parent = if (property.parent != null) {
                    property.parent.path!!
                } else {
                    rt
                }
                property.path = parent.get<Any>(property.name)

            } else {
                val path = property.parent?.path ?: rt
                if (property.isList) {
                    val joinList = (getFirstJoin(property) ?: rt).joinList<Any, Any>(property.name, JoinType.LEFT)
                        .alias(property.alias.replace(".", "_") + "_LIST")
                    selections.add(joinList)
                } else {
                    selections.add(path.get<Any>(property.name).alias(property.alias))
                }
            }
        }


        val query = if (search.isNotBlank()) {
            val node = RsqlParserFactory.instance().parse(search)
            val spec: Specification<Any> = node.accept(JpaRsqlVisitor())
            cq.where(spec.toPredicate(rt, cq, cb))
        } else cq

        selections.add(rt.get<Any>("id").alias("id"))


        query.multiselect(
            selections as List<Selection<Any>>
        )

        val resultList = entityManager.createQuery(query).resultList

        //TODO list join and list collection
        val result = resultList.map { tuple ->
            tuple.elements.mapIndexed { index, tupleElement ->
                val alias = tupleElement.alias.replace("_", ".")
                alias to tuple[index]
            }.toMap()
        }
        // result.groupBy { it.entries.first { e -> e.key == "id" } }.
        val finalResult = result.groupBy { it.entries.first { e -> e.key == "id" } }.values.map { list ->
            list.flatMap { it.entries }.filter { it.key.contains("LIST") }.groupBy { it.key }.map {
                it.key.replace(".LIST", "") to it.value.map { v -> v.value }.distinct()
            }.toMap() + list.flatMap { it.entries }.filter { !it.key.contains("LIST") }.distinctBy { it.key }
                .associate {
                    it.key to it.value
                }
        }.map { map ->
            JsonUnflattener.unflattenAsMap(map)
        }

        return finalResult.toMutableList()
    }


    fun getFirstJoin(property: GraphQlProperty): Join<*, *>? {
        return if (property.parent?.joinType != null) {
            property.parent.path as Join<*, *>
        } else if (property.parent == null) {
            null
        } else {
            getFirstJoin(property.parent)
        }
    }


}

fun SelectedField.asAlias(): String {
    return this.qualifiedName.replace("/", ".")
}

fun DataFetchingFieldSelectionSet.asProperty(): List<GraphQlProperty> {


    val list = mutableListOf<GraphQlProperty>()

    for (i in this.fields.indices) {
        val field = this.fields[i]

        val directives = field.fieldDefinitions[0].appliedDirectives
        val joinTypeValue =
            (directives.firstOrNull { d -> d.name == "join" }?.arguments?.firstOrNull()?.argumentValue?.value as EnumValue?)

        val isEmbedded = directives.any { d -> d.name == "embedded" }

        val joinType = if (joinTypeValue != null) {
            JoinType.valueOf(joinTypeValue.name)
        } else {
            null
        }
        val property = GraphQlProperty(
            name = field.name,
            alias = field.asAlias(),
            joinType = joinType,
            isList = (field.fieldDefinitions[0].definition?.type is ListType),
            parent = list.firstOrNull { p -> p.alias == field.parentField.asAlias() },
            isEmbedded = isEmbedded
        )

        list.add(
            property
        )
    }

    return list

}
