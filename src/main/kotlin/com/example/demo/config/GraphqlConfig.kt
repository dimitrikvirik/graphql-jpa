package com.example.demo.config

import com.example.demo.domain.Employee
import com.github.ichanzhar.rsql.JpaRsqlVisitor
import com.github.ichanzhar.rsql.utils.RsqlParserFactory
import com.github.wnameless.json.unflattener.JsonUnflattener
import graphql.language.EnumValue
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
    val joinType: JoinType? = null,
    var join: Join<*, *>? = null,
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
                    property.parent.path ?: property.parent.join!!
                } else {
                    rt
                }
                property.path = parent.get<Any>(property.name)

            } else {
                selections.add((property.parent?.path ?: rt).get<Any>(property.name).alias(property.alias))
            }
        }


        val node = RsqlParserFactory.instance().parse(search)
        val spec: Specification<Any> = node.accept(JpaRsqlVisitor())


        val query = cq.where(spec.toPredicate(rt, cq, cb)).multiselect(
            selections as List<Selection<*>>?
        )
        val resultList = entityManager.createQuery(query).resultList

        val result = resultList.map { tuple ->
            val map = tuple.elements.mapIndexed { index, tupleElement ->
                val alias = tupleElement.alias
                alias to tuple[index]
            }.toMap()
            JsonUnflattener.unflattenAsMap(map)
        }


        return result.toMutableList()
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
            parent = list.firstOrNull { p -> p.alias == field.parentField.asAlias() },
            isEmbedded = isEmbedded
        )

        list.add(
            property
        )
    }

    return list

}
